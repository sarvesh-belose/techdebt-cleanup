package com.enterprise.techdebt.credentials;

import com.enterprise.techdebt.domain.CredentialKind;
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
import java.util.Base64;

/**
 * Live-validates a SonarQube token by reaching the server and then asking it to
 * vouch for the token. Two-step so we can distinguish "wrong URL" from
 * "wrong token".
 */
@Component
public class SonarQubeCredentialValidator implements CredentialValidator {

    private static final Logger log = LoggerFactory.getLogger(SonarQubeCredentialValidator.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SonarQubeCredentialValidator(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), objectMapper);
    }

    public SonarQubeCredentialValidator(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public CredentialKind kind() {
        return CredentialKind.SONARQUBE;
    }

    @Override
    public ValidationResult validate(String baseUrl, String secret) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return ValidationResult.badFormat("Base URL is required");
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return ValidationResult.badFormat("URL must start with http:// or https://");
        }
        if (secret == null || secret.isBlank()) {
            return ValidationResult.badFormat("Token is required");
        }
        String clean = baseUrl.replaceAll("/$", "");

        // Step 1: ping (no auth) — confirms URL + reachability
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(clean + "/api/system/ping"))
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return ValidationResult.unreachable("SonarQube ping returned HTTP " + resp.statusCode());
            }
            if (!"pong".equals(resp.body().trim())) {
                return ValidationResult.unreachable("Expected 'pong' from " + clean
                        + "/api/system/ping (is this really a SonarQube server?)");
            }
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            return ValidationResult.unreachable("Could not reach " + clean + ": " + e.getMessage());
        } catch (Exception e) {
            log.debug("Sonar ping failed", e);
            return ValidationResult.unreachable("Network error contacting " + clean + ": " + e.getMessage());
        }

        // Step 2: token validation
        String basic = Base64.getEncoder().encodeToString((secret + ":").getBytes(StandardCharsets.UTF_8));
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(clean + "/api/authentication/validate"))
                            .GET()
                            .header("Authorization", "Basic " + basic)
                            .header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(10))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 401 || code == 403) {
                return ValidationResult.badCredentials("SonarQube rejected the token (HTTP " + code + ")");
            }
            if (code / 100 != 2) {
                return ValidationResult.upstreamError("SonarQube returned HTTP " + code);
            }
            JsonNode body = objectMapper.readTree(resp.body());
            if (body.path("valid").asBoolean(false)) {
                return ValidationResult.ok("SonarQube validated the token");
            }
            return ValidationResult.badCredentials("SonarQube reports the token as invalid");
        } catch (Exception e) {
            log.debug("Sonar token validation failed", e);
            return ValidationResult.upstreamError("Token validation failed: " + e.getMessage());
        }
    }
}
