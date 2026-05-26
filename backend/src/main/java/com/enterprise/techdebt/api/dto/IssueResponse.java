package com.enterprise.techdebt.api.dto;

import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.IssueStatus;
import com.enterprise.techdebt.domain.Severity;

import java.time.Instant;

public record IssueResponse(
        Long id,
        Long projectId,
        IssueSource source,
        String externalId,
        Severity severity,
        IssueStatus status,
        String title,
        String description,
        String filePath,
        Integer lineNumber,
        String cwe,
        String cve,
        String componentName,
        String currentVersion,
        String safeVersion,
        Boolean transitive,
        String wontFixReason,
        Instant firstSeen,
        Instant lastSeen
) {
    public static IssueResponse from(Issue i) {
        return new IssueResponse(
                i.getId(),
                i.getProjectId(),
                i.getSource(),
                i.getExternalId(),
                i.getSeverity(),
                i.getStatus(),
                i.getTitle(),
                i.getDescription(),
                i.getFilePath(),
                i.getLineNumber(),
                i.getCwe(),
                i.getCve(),
                i.getComponentName(),
                i.getCurrentVersion(),
                i.getSafeVersion(),
                i.getIsTransitive(),
                i.getWontFixReason(),
                i.getFirstSeen(),
                i.getLastSeen()
        );
    }
}
