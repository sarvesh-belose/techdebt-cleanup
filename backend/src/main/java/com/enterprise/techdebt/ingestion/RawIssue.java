package com.enterprise.techdebt.ingestion;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.IssueStatus;
import com.enterprise.techdebt.domain.Severity;
import lombok.Builder;
import lombok.Value;

/**
 * Normalized issue record returned by a connector. The ingestion service maps
 * these onto the canonical Issue entity, dedup'ing by (project, source, externalId).
 *
 * Optional fields are documented per-source in connector implementations.
 */
@Value
@Builder
public class RawIssue {
    IssueSource source;

    /** Stable identifier from the source system. Unique per (project, source). */
    String externalId;

    Severity severity;

    String title;
    String description;

    // SAST / Sonar
    String filePath;
    Integer lineNumber;
    String cwe;

    // SCA
    String cve;
    String componentName;
    String currentVersion;
    String safeVersion;
    Boolean transitive;
    String transitivePath;     // JSON-encoded path through dependency tree

    /** Raw upstream payload, JSON-serialized, for forensics. */
    String rawPayload;

    /**
     * If the upstream system indicates the finding is accepted/mitigated, the connector
     * sets this to {@link IssueStatus#WONT_FIX}. The ingestion service honors it only on
     * INITIAL CREATE — once an issue is in our DB, our own status takes precedence to
     * avoid clobbering local decisions.
     */
    IssueStatus recommendedInitialStatus;

    String recommendedStatusReason;
}
