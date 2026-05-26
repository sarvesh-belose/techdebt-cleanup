package com.enterprise.techdebt.api.dto;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.ScanSourceConfig;

import java.time.Instant;

public record ScanSourceConfigResponse(
        Long id,
        Long projectId,
        IssueSource source,
        String externalProjectId,
        String apiBaseUrl,
        boolean enabled,
        String cronExpression,
        Integer coverageThresholdPct,
        Instant createdAt,
        Instant updatedAt
) {
    public static ScanSourceConfigResponse from(ScanSourceConfig c) {
        return new ScanSourceConfigResponse(
                c.getId(),
                c.getProjectId(),
                c.getSource(),
                c.getExternalProjectId(),
                c.getApiBaseUrl(),
                c.isEnabled(),
                c.getCronExpression(),
                c.getCoverageThresholdPct(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
