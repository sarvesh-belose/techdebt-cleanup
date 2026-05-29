package com.enterprise.techdebt.api.dto;

import com.enterprise.techdebt.domain.CredentialKind;
import com.enterprise.techdebt.domain.PlatformCredential;

import java.time.Instant;

/**
 * Public view of a stored credential. Never exposes the secret value — only
 * the kind, base URL, and last-validation state so the UI can show status.
 */
public record CredentialResponse(
        CredentialKind kind,
        String baseUrl,
        Instant lastValidatedAt,
        Boolean lastValidatedOk,
        String lastValidatedMessage,
        Instant updatedAt,
        Long updatedByUserId
) {
    public static CredentialResponse from(PlatformCredential c) {
        return new CredentialResponse(
                c.getKind(),
                c.getBaseUrl(),
                c.getLastValidatedAt(),
                c.getLastValidatedOk(),
                c.getLastValidatedMessage(),
                c.getUpdatedAt(),
                c.getUpdatedByUserId()
        );
    }
}
