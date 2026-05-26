package com.enterprise.techdebt.ingestion.sonarqube;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.ScanSourceConfig;
import com.enterprise.techdebt.domain.Severity;
import com.enterprise.techdebt.ingestion.ConnectorException;
import com.enterprise.techdebt.ingestion.IssueSourceConnector;
import com.enterprise.techdebt.ingestion.RawIssue;
import com.enterprise.techdebt.resilience.CircuitBreaker;
import com.enterprise.techdebt.resilience.CircuitBreakerRegistry;
import com.enterprise.techdebt.secret.SecretStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * SonarQube connector. Handles BOTH:
 *   - code issues via /api/issues/search?componentKeys={key}&resolved=false
 *   - coverage gaps via /api/measures/component_tree?metricKeys=coverage,line_coverage,uncovered_lines
 *     synthesized as JUNIT_COVERAGE RawIssues, one per file below the configured threshold.
 *
 * Auth: SonarQube user token, sent as HTTP Basic with token as username and empty password
 * (the standard SonarQube scheme).
 *
 * Decision (E3): we deliberately use Sonar's measures API rather than parsing JaCoCo XML
 * out of pipeline artifacts — Sonar already ingests JaCoCo, so going through it gives us
 * one source of truth.
 */
@Component
public class SonarQubeConnector implements IssueSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(SonarQubeConnector.class);
    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final int MAX_PAGES = 40;          // SonarQube caps at 10k issues anyway
    private static final int DEFAULT_COVERAGE_THRESHOLD_PCT = 80;

    private final SecretStore secretStore;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker breaker;

    @Autowired
    public SonarQubeConnector(SecretStore secretStore, ObjectMapper objectMapper,
                              CircuitBreakerRegistry breakers) {
        this(secretStore,
             HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
             objectMapper,
             breakers.forName("sonarqube"));
    }

    public SonarQubeConnector(SecretStore secretStore, HttpClient httpClient, ObjectMapper objectMapper,
                              CircuitBreaker breaker) {
        this.secretStore = secretStore;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.breaker = breaker;
    }

    @Override
    public IssueSource source() {
        // Note: this connector emits TWO sources of RawIssues. The ingestion service
        // treats SONARQUBE as the primary source; coverage issues are filtered by their
        // source field on the RawIssue itself, not by IssueSourceConnector.source().
        return IssueSource.SONARQUBE;
    }

    @Override
    public List<RawIssue> fetchIssues(ScanSourceConfig cfg) {
        if (cfg.getSource() != IssueSource.SONARQUBE) {
            throw new ConnectorException("config source=" + cfg.getSource() + ", expected SONARQUBE");
        }
        String token = secretStore.resolve(cfg.getSecretKeyVaultRef());
        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
        URI apiBase = URI.create(cfg.getApiBaseUrl());

        List<RawIssue> out = new ArrayList<>();
        fetchCodeIssues(cfg, apiBase, authHeader, out);
        int threshold = cfg.getCoverageThresholdPct() != null
                ? cfg.getCoverageThresholdPct()
                : DEFAULT_COVERAGE_THRESHOLD_PCT;
        fetchCoverageGaps(cfg, apiBase, authHeader, threshold, out);
        log.info("SonarQube fetched {} records (issues + coverage) for project {}",
                out.size(), cfg.getExternalProjectId());
        return out;
    }

    private void fetchCodeIssues(ScanSourceConfig cfg, URI apiBase, String authHeader, List<RawIssue> out) {
        int page = 1;
        while (page <= MAX_PAGES) {
            String path = "/api/issues/search?componentKeys=" + encode(cfg.getExternalProjectId())
                    + "&resolved=false&p=" + page + "&ps=" + DEFAULT_PAGE_SIZE;
            JsonNode root = get(apiBase, path, authHeader);
            JsonNode issues = root.path("issues");
            if (!issues.isArray() || issues.isEmpty()) break;
            for (JsonNode i : issues) {
                out.add(parseCodeIssue(i));
            }
            JsonNode paging = root.path("paging");
            int total = paging.path("total").asInt(0);
            int current = paging.path("pageIndex").asInt(page);
            int size = paging.path("pageSize").asInt(DEFAULT_PAGE_SIZE);
            if (current * size >= total) break;
            page++;
        }
    }

    private void fetchCoverageGaps(ScanSourceConfig cfg, URI apiBase, String authHeader,
                                   int thresholdPct, List<RawIssue> out) {
        int page = 1;
        String metrics = "coverage,line_coverage,uncovered_lines,uncovered_conditions";
        while (page <= MAX_PAGES) {
            String path = "/api/measures/component_tree?component=" + encode(cfg.getExternalProjectId())
                    + "&metricKeys=" + metrics
                    + "&strategy=leaves&qualifiers=FIL"
                    + "&p=" + page + "&ps=" + DEFAULT_PAGE_SIZE;
            JsonNode root = get(apiBase, path, authHeader);
            JsonNode components = root.path("components");
            if (!components.isArray() || components.isEmpty()) break;
            for (JsonNode c : components) {
                RawIssue coverageGap = parseCoverageComponent(c, thresholdPct);
                if (coverageGap != null) {
                    out.add(coverageGap);
                }
            }
            JsonNode paging = root.path("paging");
            int total = paging.path("total").asInt(0);
            int current = paging.path("pageIndex").asInt(page);
            int size = paging.path("pageSize").asInt(DEFAULT_PAGE_SIZE);
            if (current * size >= total) break;
            page++;
        }
    }

    private RawIssue parseCodeIssue(JsonNode i) {
        String key = textOrNull(i, "key");
        String rule = textOrNull(i, "rule");
        Severity sev = parseSeverity(textOrNull(i, "severity"));
        String message = textOrNull(i, "message");
        String component = textOrNull(i, "component");
        // SonarQube component looks like "projectKey:src/main/java/.../File.java"
        String filePath = component != null && component.contains(":") ? component.substring(component.indexOf(':') + 1) : component;
        Integer line = i.has("line") && !i.get("line").isNull() ? i.get("line").asInt() : null;
        return RawIssue.builder()
                .source(IssueSource.SONARQUBE)
                .externalId(key != null ? key : (rule + "@" + component + ":" + line))
                .severity(sev)
                .title(rule != null ? rule + ": " + (message != null ? message : "") : message)
                .description(message)
                .filePath(filePath)
                .lineNumber(line)
                .rawPayload(i.toString())
                .build();
    }

    private RawIssue parseCoverageComponent(JsonNode c, int thresholdPct) {
        Double coverage = pickMeasure(c, "coverage");
        if (coverage == null || coverage >= thresholdPct) return null;

        String key = textOrNull(c, "key");
        String name = textOrNull(c, "name");
        String path = textOrNull(c, "path");
        String displayPath = path != null ? path : name;

        // Severity scales with how far below threshold the file is.
        Severity sev;
        if (coverage < 25) sev = Severity.HIGH;
        else if (coverage < 50) sev = Severity.MEDIUM;
        else if (coverage < thresholdPct) sev = Severity.LOW;
        else sev = Severity.INFO;

        String title = String.format("Coverage below threshold: %.1f%% (threshold %d%%) — %s",
                coverage, thresholdPct, displayPath);
        return RawIssue.builder()
                .source(IssueSource.JUNIT_COVERAGE)
                .externalId("coverage:" + key)
                .severity(sev)
                .title(title)
                .description("File coverage is " + String.format("%.1f%%", coverage)
                        + ", threshold is " + thresholdPct + "%.")
                .filePath(displayPath)
                .rawPayload(c.toString())
                .build();
    }

    private Double pickMeasure(JsonNode component, String metric) {
        JsonNode measures = component.path("measures");
        if (!measures.isArray()) return null;
        for (JsonNode m : measures) {
            if (metric.equals(textOrNull(m, "metric"))) {
                String v = textOrNull(m, "value");
                if (v == null) return null;
                try {
                    return Double.parseDouble(v);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private JsonNode get(URI apiBase, String path, String authHeader) {
        URI fullUri = apiBase.resolve(path);
        HttpRequest req = HttpRequest.newBuilder(fullUri)
                .GET()
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> resp;
        try {
            resp = breaker.call(() -> {
                try {
                    return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                } catch (Exception inner) {
                    throw new ConnectorException("SonarQube fetch failed " + path + ": " + inner.getMessage(), inner);
                }
            });
        } catch (CircuitBreaker.CircuitOpenException e) {
            throw new ConnectorException("SonarQube is unavailable (circuit open) — " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ConnectorException("SonarQube fetch failed " + path + ": " + e.getMessage(), e);
        }
        if (resp.statusCode() / 100 != 2) {
            throw new ConnectorException("SonarQube HTTP " + resp.statusCode() + " on " + path
                    + ": " + truncate(resp.body(), 400));
        }
        try {
            return objectMapper.readTree(resp.body());
        } catch (Exception e) {
            throw new ConnectorException("SonarQube response was not valid JSON for " + path, e);
        }
    }

    private static Severity parseSeverity(String v) {
        if (v == null) return Severity.MEDIUM;
        return switch (v.trim().toUpperCase()) {
            case "BLOCKER", "CRITICAL" -> Severity.CRITICAL;
            case "MAJOR" -> Severity.HIGH;
            case "MINOR" -> Severity.MEDIUM;
            case "INFO" -> Severity.LOW;
            default -> Severity.MEDIUM;
        };
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
