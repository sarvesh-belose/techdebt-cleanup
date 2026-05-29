package com.enterprise.techdebt.credentials;

import com.enterprise.techdebt.domain.CredentialKind;
import com.enterprise.techdebt.ingestion.veracode.VeracodeHmacSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Live-validates a Veracode credential pair (apiId:apiKey, both hex). Mirrors the
 * HMAC signing the production connectors do, so a successful validation here is
 * a real proof the scan path will work.
 */
@Component
public class VeracodeCredentialValidator implements CredentialValidator {

    private static final Logger log = LoggerFactory.getLogger(VeracodeCredentialValidator.class);
    private static final String PROBE_PATH = "/appsec/v1/applications?size=1";

    private final HttpClient httpClient;
    private final VeracodeHmacSigner signer;

    @Autowired
    public VeracodeCredentialValidator() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
             new VeracodeHmacSigner());
    }

    // Test-friendly constructor.
    public VeracodeCredentialValidator(HttpClient httpClient, VeracodeHmacSigner signer) {
        this.httpClient = httpClient;
        this.signer = signer;
    }

    @Override
    public CredentialKind kind() {
        return CredentialKind.VERACODE;
    }

    @Override
    public ValidationResult validate(String baseUrl, String secret) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return ValidationResult.badFormat("Base URL is required");
        }
        if (secret == null || secret.isBlank()) {
            return ValidationResult.badFormat("Credentials are required");
        }
        int colon = secret.indexOf(':');
        if (colon <= 0 || colon == secret.length() - 1) {
            return ValidationResult.badFormat("Credentials must be 'apiId:apiKey' (both hex, colon-separated)");
        }
        String apiId = secret.substring(0, colon);
        String apiKey = secret.substring(colon + 1);
        if (!apiId.matches("^[0-9a-fA-F]{8,}$")) {
            return ValidationResult.badFormat("API ID must be hex (got " + apiId.length() + " chars)");
        }
        if (!apiKey.matches("^[0-9a-fA-F]{16,}$")) {
            return ValidationResult.badFormat("API key must be hex (got " + apiKey.length() + " chars)");
        }

        URI uri = URI.create(baseUrl.replaceAll("/$", "") + PROBE_PATH);
        String authHeader;
        try {
            authHeader = signer.authorizationHeader(apiId, apiKey, "GET", PROBE_PATH);
        } catch (RuntimeException ex) {
            return ValidationResult.badFormat("Could not build HMAC header: " + ex.getMessage());
        }
        HttpRequest req = HttpRequest.newBuilder(uri)
                .GET()
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code / 100 == 2) {
                return ValidationResult.ok("Veracode accepted the HMAC-signed probe (HTTP " + code + ")");
            }
            if (code == 401 || code == 403) {
                return ValidationResult.badCredentials("Veracode rejected the credentials (HTTP " + code + ")");
            }
            return ValidationResult.upstreamError("Veracode returned HTTP " + code);
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            return ValidationResult.unreachable("Could not reach " + baseUrl + ": " + e.getMessage());
        } catch (Exception e) {
            log.debug("Veracode validation error", e);
            return ValidationResult.unreachable("Network error: " + e.getMessage());
        }
    }
}
