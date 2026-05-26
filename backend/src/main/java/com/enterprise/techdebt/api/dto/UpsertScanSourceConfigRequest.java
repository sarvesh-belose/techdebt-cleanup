package com.enterprise.techdebt.api.dto;

import com.enterprise.techdebt.domain.IssueSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertScanSourceConfigRequest(
        @NotNull IssueSource source,
        @NotBlank @Size(max = 500) String externalProjectId,
        @NotBlank @Size(max = 1000) String apiBaseUrl,
        // Plain secret value — never persisted in the DB; goes straight to SecretStore.
        @NotBlank String secretValue,
        @Size(max = 100) String cronExpression,
        @Min(0) Integer coverageThresholdPct,
        Boolean enabled
) {
}
