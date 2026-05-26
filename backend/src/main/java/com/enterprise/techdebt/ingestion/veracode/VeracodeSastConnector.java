package com.enterprise.techdebt.ingestion.veracode;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.IssueStatus;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Fetches Veracode SAST findings for one application.
 *
 * Endpoint (per Veracode appsec API v2):
 *   GET /appsec/v2/applications/{appGuid}/findings?scan_type=STATIC&page={n}&size={s}
 *
 * Mitigation status: when a finding is in an accepted state (ACCEPTED / APPROVED)
 * the connector still returns it but flags the mitigation reason in the raw payload —
 * the ingestion service treats accepted findings as {@code WONT_FIX} on initial create.
 * That logic lives in the ingestion layer, not here, so this connector stays a pure
 * mapper.
 */
@Component
public class VeracodeSastConnector implements IssueSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(VeracodeSastConnector.class);
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGES = 200;

    private final SecretStore secretStore;
    private final HttpClient httpClient;
    private final VeracodeHmacSigner signer;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker breaker;

    @Autowired
    public VeracodeSastConnector(SecretStore secretStore, ObjectMapper objectMapper,
                                 CircuitBreakerRegistry breakers) {
        this(secretStore,
             HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
             new VeracodeHmacSigner(),
             objectMapper,
             breakers.forName("veracode-sast"));
    }

    public VeracodeSastConnector(SecretStore secretStore, HttpClient httpClient,
                                 VeracodeHmacSigner signer, ObjectMapper objectMapper,
                                 CircuitBreaker breaker) {
        this.secretStore = secretStore;
        this.httpClient = httpClient;
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.breaker = breaker;
    }

    @Override
    public IssueSource source() {
        return IssueSource.VERACODE_SAST;
    }

    @Override
    public List<RawIssue> fetchIssues(ScanSourceConfig cfg) {
        if (cfg.getSource() != IssueSource.VERACODE_SAST) {
            throw new ConnectorException("config source=" + cfg.getSource() + ", expected VERACODE_SAST");
        }
        Credentials creds = resolveCredentials(cfg);
        String basePath = "/appsec/v2/applications/" + cfg.getExternalProjectId() + "/findings";
        URI apiBase = URI.create(cfg.getApiBaseUrl());

        List<RawIssue> out = new ArrayList<>();
        int page = 0;
        while (page < MAX_PAGES) {
            String pathWithQuery = basePath + "?scan_type=STATIC&page=" + page + "&size=" + DEFAULT_PAGE_SIZE;
            URI fullUri = apiBase.resolve(pathWithQuery);
            String authHeader = signer.authorizationHeader(creds.apiId, creds.apiKey, "GET", pathWithQuery);

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
                        throw new ConnectorException("Veracode SAST fetch failed: " + inner.getMessage(), inner);
                    }
                });
            } catch (CircuitBreaker.CircuitOpenException e) {
                throw new ConnectorException("Veracode SAST is unavailable (circuit open) — " + e.getMessage(), e);
            } catch (RuntimeException e) {
                throw new ConnectorException("Veracode SAST fetch failed: " + e.getMessage(), e);
            }
            if (resp.statusCode() / 100 != 2) {
                throw new ConnectorException("Veracode SAST HTTP " + resp.statusCode() + ": "
                        + truncate(resp.body(), 400));
            }
            JsonNode root;
            try {
                root = objectMapper.readTree(resp.body());
            } catch (Exception e) {
                throw new ConnectorException("Veracode SAST response was not valid JSON", e);
            }

            JsonNode findings = pickEmbeddedFindings(root);
            if (findings == null || !findings.isArray() || findings.size() == 0) {
                break;
            }
            for (JsonNode node : findings) {
                out.add(parseFinding(node));
            }
            if (!hasNextPage(root)) break;
            page++;
        }
        log.info("Veracode SAST fetched {} findings for app {}", out.size(), cfg.getExternalProjectId());
        return out;
    }

    private RawIssue parseFinding(JsonNode node) {
        String findingId = firstNonBlank(
                textOrNull(node, "issue_id"),
                textOrNull(node, "id"),
                textOrNull(node, "guid"));
        if (findingId == null) {
            throw new ConnectorException("Veracode SAST finding missing id: " + truncate(node.toString(), 200));
        }
        JsonNode details = node.has("finding_details") ? node.get("finding_details") : node;

        String cwe = firstNonBlank(
                textOrNull(details.path("cwe"), "id"),
                textOrNull(details, "cwe_id"),
                textOrNull(node, "cwe_id"));
        String filePath = firstNonBlank(
                textOrNull(details, "file_path"),
                textOrNull(node, "file_path"));
        Integer lineNumber = optInt(details, "file_line_number");
        if (lineNumber == null) lineNumber = optInt(node, "line_number");

        Severity severity = parseSeverity(firstNonBlank(
                textOrNull(node, "severity"),
                textOrNull(details, "severity"),
                Integer.toString(details.path("severity").asInt(-1))));

        String title = firstNonBlank(
                textOrNull(details.path("cwe"), "name"),
                textOrNull(node, "issue_type"),
                "SAST finding " + findingId);
        String description = firstNonBlank(
                textOrNull(details.path("cwe"), "description"),
                textOrNull(node, "description"));

        // D2 — propagate mitigation/acceptance state. Veracode SAST encodes this in
        // finding_status.resolution_status. ACCEPTED/APPROVED → WONT_FIX on first ingest.
        IssueStatus recommendedStatus = null;
        String recommendedReason = null;
        JsonNode status = node.path("finding_status");
        String resolution = firstNonBlank(
                textOrNull(status, "resolution_status"),
                textOrNull(status, "resolution"),
                textOrNull(node, "resolution_status"));
        if (resolution != null) {
            String upper = resolution.toUpperCase();
            if (upper.equals("ACCEPTED") || upper.equals("APPROVED") || upper.equals("WONT_FIX")) {
                recommendedStatus = IssueStatus.WONT_FIX;
                recommendedReason = "Veracode mitigation: " + resolution;
            }
        }

        return RawIssue.builder()
                .source(IssueSource.VERACODE_SAST)
                .externalId(findingId)
                .severity(severity)
                .title(truncate(title, 1000))
                .description(description)
                .cwe(cwe)
                .filePath(filePath)
                .lineNumber(lineNumber)
                .rawPayload(node.toString())
                .recommendedInitialStatus(recommendedStatus)
                .recommendedStatusReason(recommendedReason)
                .build();
    }

    private JsonNode pickEmbeddedFindings(JsonNode root) {
        JsonNode embedded = root.path("_embedded");
        if (embedded.path("findings").isArray()) return embedded.get("findings");
        for (Iterator<String> it = embedded.fieldNames(); it.hasNext(); ) {
            JsonNode candidate = embedded.get(it.next());
            if (candidate.isArray()) return candidate;
        }
        if (root.path("findings").isArray()) return root.get("findings");
        if (root.isArray()) return root;
        return null;
    }

    private boolean hasNextPage(JsonNode root) {
        JsonNode next = root.path("_links").path("next");
        if (next.isObject() && next.has("href")) return true;
        JsonNode pageInfo = root.path("page");
        if (pageInfo.isObject() && pageInfo.has("number") && pageInfo.has("totalPages")) {
            return pageInfo.get("number").asInt() + 1 < pageInfo.get("totalPages").asInt();
        }
        return false;
    }

    private Credentials resolveCredentials(ScanSourceConfig cfg) {
        String secret = secretStore.resolve(cfg.getSecretKeyVaultRef());
        int colon = secret.indexOf(':');
        if (colon <= 0 || colon == secret.length() - 1) {
            throw new ConnectorException("Veracode credentials must be '<apiId>:<apiKey>'");
        }
        return new Credentials(secret.substring(0, colon), secret.substring(colon + 1));
    }

    private static Severity parseSeverity(String v) {
        if (v == null) return Severity.MEDIUM;
        String upper = v.trim().toUpperCase();
        return switch (upper) {
            case "CRITICAL", "VERY HIGH", "5" -> Severity.CRITICAL;
            case "HIGH", "4" -> Severity.HIGH;
            case "MEDIUM", "3" -> Severity.MEDIUM;
            case "LOW", "2" -> Severity.LOW;
            case "INFO", "INFORMATIONAL", "VERY LOW", "1", "0" -> Severity.INFO;
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

    private static Integer optInt(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.canConvertToInt()) return null;
        return v.asInt();
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record Credentials(String apiId, String apiKey) {}
}
