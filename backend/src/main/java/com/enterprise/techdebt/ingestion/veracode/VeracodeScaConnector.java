package com.enterprise.techdebt.ingestion.veracode;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Fetches Veracode SCA findings for one project (one Veracode workspace).
 *
 * API contract (per Veracode SCA Agent / Workspaces v3):
 *   GET /srcclr/v3/workspaces/{workspaceId}/issues?page={n}&size={s}
 * Pagination follows the standard HAL/Spring-style `_links.next.href` convention;
 * we stop on the absence of `_links.next`.
 *
 * Auth: HMAC header computed by {@link VeracodeHmacSigner}. Credentials format:
 *   secret_key_vault_ref → "<apiId>:<apiKey>" (both hex). Anything else fails fast.
 */
@Component
public class VeracodeScaConnector implements IssueSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(VeracodeScaConnector.class);
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGES = 200;

    private final SecretStore secretStore;
    private final HttpClient httpClient;
    private final VeracodeHmacSigner signer;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker breaker;

    @Autowired
    public VeracodeScaConnector(SecretStore secretStore, ObjectMapper objectMapper,
                                CircuitBreakerRegistry breakers) {
        this(secretStore,
             HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
             new VeracodeHmacSigner(),
             objectMapper,
             breakers.forName("veracode-sca"));
    }

    // Test-friendly constructor.
    public VeracodeScaConnector(SecretStore secretStore, HttpClient httpClient,
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
        return IssueSource.VERACODE_SCA;
    }

    @Override
    public List<RawIssue> fetchIssues(ScanSourceConfig cfg) {
        if (cfg.getSource() != IssueSource.VERACODE_SCA) {
            throw new ConnectorException("config source=" + cfg.getSource() + ", expected VERACODE_SCA");
        }
        Credentials creds = resolveCredentials(cfg);
        String basePath = "/srcclr/v3/workspaces/" + cfg.getExternalProjectId() + "/issues";
        URI apiBase = URI.create(cfg.getApiBaseUrl());

        List<RawIssue> out = new ArrayList<>();
        int page = 0;
        while (page < MAX_PAGES) {
            String pathWithQuery = basePath + "?page=" + page + "&size=" + DEFAULT_PAGE_SIZE;
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
                        throw new ConnectorException("Veracode SCA fetch failed: " + inner.getMessage(), inner);
                    }
                });
            } catch (CircuitBreaker.CircuitOpenException e) {
                throw new ConnectorException("Veracode SCA is unavailable (circuit open) — " + e.getMessage(), e);
            } catch (RuntimeException e) {
                throw new ConnectorException("Veracode SCA fetch failed: " + e.getMessage(), e);
            }
            if (resp.statusCode() / 100 != 2) {
                throw new ConnectorException("Veracode SCA HTTP " + resp.statusCode() + ": "
                        + truncate(resp.body(), 400));
            }
            JsonNode root;
            try {
                root = objectMapper.readTree(resp.body());
            } catch (Exception e) {
                throw new ConnectorException("Veracode SCA response was not valid JSON", e);
            }

            JsonNode issues = pickEmbeddedIssues(root);
            if (issues == null || !issues.isArray() || issues.size() == 0) {
                break;
            }
            for (JsonNode node : issues) {
                out.add(parseIssue(node));
            }

            if (!hasNextPage(root)) {
                break;
            }
            page++;
        }
        log.info("Veracode SCA fetched {} findings for workspace {}", out.size(), cfg.getExternalProjectId());
        return out;
    }

    private RawIssue parseIssue(JsonNode node) {
        String findingId = textOrNull(node, "id");
        if (findingId == null) findingId = textOrNull(node, "guid");
        if (findingId == null) findingId = textOrNull(node, "finding_id");
        if (findingId == null) {
            throw new ConnectorException("Veracode SCA issue missing id/guid: " + truncate(node.toString(), 200));
        }

        JsonNode vuln = node.path("vulnerability");
        JsonNode lib = node.path("library");

        String cve = firstNonBlank(
                textOrNull(vuln, "cve"),
                textOrNull(node, "cve"),
                textOrNull(node.path("issue_summary"), "cve"));

        String component = firstNonBlank(
                textOrNull(lib, "name"),
                textOrNull(lib, "coordinate1") + ":" + textOrNull(lib, "coordinate2"),
                textOrNull(node, "library_name"));

        String currentVersion = firstNonBlank(
                textOrNull(lib, "version"),
                textOrNull(node, "version"));

        String safeVersion = firstNonBlank(
                textOrNull(vuln, "fix_version"),
                textOrNull(vuln, "patched_versions"),
                textOrNull(node, "fix_version"));

        Boolean transitive = node.has("transitive")
                ? node.get("transitive").asBoolean()
                : (node.path("library_path").isArray() && node.path("library_path").size() > 1);

        String transitivePath = node.path("library_path").isArray()
                ? node.path("library_path").toString()
                : null;

        Severity severity = parseSeverity(firstNonBlank(
                textOrNull(node, "severity"),
                textOrNull(vuln, "severity"),
                textOrNull(node, "severity_text")));

        String title = firstNonBlank(
                textOrNull(vuln, "title"),
                "CVE " + (cve != null ? cve : "(unknown)") + " in " + (component != null ? component : "?"));
        String description = firstNonBlank(
                textOrNull(vuln, "overview"),
                textOrNull(vuln, "description"),
                textOrNull(node, "issue_summary"));

        return RawIssue.builder()
                .source(IssueSource.VERACODE_SCA)
                .externalId(findingId)
                .severity(severity)
                .title(truncate(title, 1000))
                .description(description)
                .cve(cve)
                .componentName(component)
                .currentVersion(currentVersion)
                .safeVersion(safeVersion)
                .transitive(transitive)
                .transitivePath(transitivePath)
                .rawPayload(node.toString())
                .build();
    }

    private JsonNode pickEmbeddedIssues(JsonNode root) {
        JsonNode embedded = root.path("_embedded");
        for (Iterator<String> it = embedded.fieldNames(); it.hasNext(); ) {
            JsonNode candidate = embedded.get(it.next());
            if (candidate.isArray()) return candidate;
        }
        if (root.path("issues").isArray()) return root.get("issues");
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
            case "CRITICAL", "5" -> Severity.CRITICAL;
            case "HIGH", "4" -> Severity.HIGH;
            case "MEDIUM", "3" -> Severity.MEDIUM;
            case "LOW", "2" -> Severity.LOW;
            case "INFO", "INFORMATIONAL", "1", "0" -> Severity.INFO;
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

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank() && !"null:null".equals(c)) return c;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record Credentials(String apiId, String apiKey) {}
}
