package com.enterprise.techdebt.api.dto;

import com.enterprise.techdebt.domain.Project;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        String azureRepoUrl,
        String defaultBranch,
        Long ownerUserId,
        boolean autoModeEnabled,
        boolean archived,
        int safetyThreshold,
        int autoRateLimitPerHour,
        String autoPolicyJson,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getAzureRepoUrl(),
                p.getDefaultBranch(),
                p.getOwnerUserId(),
                p.isAutoModeEnabled(),
                p.isArchived(),
                p.getSafetyThreshold(),
                p.getAutoRateLimitPerHour(),
                p.getAutoPolicyJson(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
