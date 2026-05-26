package com.enterprise.techdebt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 1000)
        @Pattern(
            regexp = "^https://[^\\s]+/_git/[^\\s]+$",
            message = "must be an Azure DevOps Git URL of the form https://.../_git/<repo>")
        String azureRepoUrl,
        @Size(max = 200) String defaultBranch
) {
    public String effectiveBranch() {
        return defaultBranch == null || defaultBranch.isBlank() ? "main" : defaultBranch.trim();
    }
}
