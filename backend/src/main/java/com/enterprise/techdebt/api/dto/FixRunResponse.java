package com.enterprise.techdebt.api.dto;

import com.enterprise.techdebt.domain.FixRun;
import com.enterprise.techdebt.domain.FixRunState;

import java.time.Instant;

public record FixRunResponse(
        Long id,
        Long projectId,
        Long initiatedByUserId,
        boolean autoMode,
        FixRunState state,
        String branchName,
        String commitSha,
        String prUrl,
        Integer safetyScore,
        String safetyBlockedReason,
        String failureReason,
        String issueIdsJson,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static FixRunResponse from(FixRun r) {
        return new FixRunResponse(
                r.getId(),
                r.getProjectId(),
                r.getInitiatedByUserId(),
                r.isAutoMode(),
                r.getState(),
                r.getBranchName(),
                r.getCommitSha(),
                r.getPrUrl(),
                r.getSafetyScore(),
                r.getSafetyBlockedReason(),
                r.getFailureReason(),
                r.getIssueIdsJson(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCompletedAt()
        );
    }
}
