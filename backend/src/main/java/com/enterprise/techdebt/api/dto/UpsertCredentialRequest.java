package com.enterprise.techdebt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertCredentialRequest(
        @NotBlank @Size(max = 1000) String baseUrl,
        @NotBlank @Size(max = 4096) String secret,
        /** When true, persist even if live validation fails. Use sparingly; audit-logged. */
        Boolean saveAnyway
) {
}
