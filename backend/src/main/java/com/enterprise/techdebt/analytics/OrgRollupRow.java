package com.enterprise.techdebt.analytics;

public record OrgRollupRow(
        Long projectId,
        String projectName,
        String source,
        String severity,
        String status,
        long count
) {
}
