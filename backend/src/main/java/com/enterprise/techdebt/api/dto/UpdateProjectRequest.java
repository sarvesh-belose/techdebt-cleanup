package com.enterprise.techdebt.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(max = 2000) String description,
        @Size(max = 1000)
        @Pattern(
            regexp = "^https://[^\\s]+/_git/[^\\s]+$",
            message = "must be an Azure DevOps Git URL of the form https://.../_git/<repo>")
        String azureRepoUrl,
        @Size(max = 200) String defaultBranch,
        Boolean autoModeEnabled,
        @Min(0) @Max(100) Integer safetyThreshold,
        @Min(0) Integer autoRateLimitPerHour,
        @Size(max = 10000) String autoPolicyJson
) {
}
