package com.enterprise.techdebt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WontFixRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
