package com.enterprise.techdebt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestCredentialRequest(
        @NotBlank @Size(max = 1000) String baseUrl,
        @NotBlank @Size(max = 4096) String secret
) {
}
