package com.enterprise.techdebt.ingestion;

import com.enterprise.techdebt.audit.AuditAction;
import com.enterprise.techdebt.audit.AuditService;
import com.enterprise.techdebt.audit.ResourceType;
import com.enterprise.techdebt.config.MdcConstants;
import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.IssueStatus;
import com.enterprise.techdebt.domain.ScanSourceConfig;
import com.enterprise.techdebt.metrics.PlatformMetrics;
import com.enterprise.techdebt.repo.IssueRepository;
import com.enterprise.techdebt.repo.ScanSourceConfigRepository;
import com.enterprise.techdebt.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates a scan: invokes a connector, dedups by (project, source, externalId),
 * upserts into the issue table, and resolves issues no longer returned upstream.
 */
@Service
public class IssueIngestionService {

    private static final Logger log = LoggerFactory.getLogger(IssueIngestionService.class);

    private final List<IssueSourceConnector> connectors;
    private final ScanSourceConfigRepository scanSourceConfigRepo;
    private final IssueRepository issueRepo;
    private final AuditService auditService;
    private final PlatformMetrics metrics;

    public IssueIngestionService(List<IssueSourceConnector> connectors,
                                 ScanSourceConfigRepository scanSourceConfigRepo,
                                 IssueRepository issueRepo,
                                 AuditService auditService,
                                 PlatformMetrics metrics) {
        this.connectors = connectors;
        this.scanSourceConfigRepo = scanSourceConfigRepo;
        this.issueRepo = issueRepo;
        this.auditService = auditService;
        this.metrics = metrics;
    }

    @Transactional
    public IngestionResult scan(CurrentUser actor, long projectId, IssueSource source) {
        long startMillis = System.currentTimeMillis();
        MDC.put(MdcConstants.PROJECT_ID, String.valueOf(projectId));
        ScanSourceConfig cfg = scanSourceConfigRepo.findByProjectIdAndSource(projectId, source)
                .orElseThrow(() -> new IllegalStateException(
                        "No scan source config for project=" + projectId + " source=" + source));
        if (!cfg.isEnabled()) {
            throw new IllegalStateException("Scan source disabled: project=" + projectId + " source=" + source);
        }
        IssueSourceConnector connector = connectorFor(source);

        auditService.record(actor, AuditAction.SCAN_TRIGGERED, ResourceType.PROJECT,
                String.valueOf(projectId), projectId, null, Map.of("source", source.name()), null);

        List<RawIssue> raw;
        try {
            raw = connector.fetchIssues(cfg);
        } catch (RuntimeException ex) {
            log.error("Scan failed project={} source={}: {}", projectId, source, ex.getMessage());
            auditService.record(actor, AuditAction.SCAN_FAILED, ResourceType.PROJECT,
                    String.valueOf(projectId), projectId, null,
                    Map.of("source", source.name(), "error", ex.getMessage()), null);
            metrics.recordScanFailure();
            metrics.recordScanDuration(source, System.currentTimeMillis() - startMillis);
            throw ex;
        }

        int created = 0;
        int updated = 0;
        // Track seen (source, externalId) so connectors that emit multiple sources
        // (e.g. SonarQube → SONARQUBE + JUNIT_COVERAGE) auto-resolve correctly per source.
        Map<IssueSource, Set<String>> seenBySource = new HashMap<>();
        EnumSet<IssueSource> sourcesReturned = EnumSet.noneOf(IssueSource.class);
        Instant now = Instant.now();

        for (RawIssue r : raw) {
            sourcesReturned.add(r.getSource());
            seenBySource.computeIfAbsent(r.getSource(), s -> new HashSet<>()).add(r.getExternalId());
            Issue existing = issueRepo.findByProjectIdAndSourceAndExternalId(
                    projectId, r.getSource(), r.getExternalId()).orElse(null);
            if (existing == null) {
                issueRepo.save(toEntity(r, projectId, now));
                created++;
            } else {
                applyUpdates(existing, r, now);
                updated++;
            }
        }

        // Auto-resolve any issue from a source we just scanned that wasn't returned this time.
        // We resolve only within sources the connector actually emitted, so absence of
        // coverage data doesn't erase code issues and vice versa.
        int resolvedExternally = 0;
        Set<IssueSource> scopes = sourcesReturned.isEmpty() ? EnumSet.of(source) : sourcesReturned;
        List<Issue> open = issueRepo.findAll().stream()
                .filter(i -> i.getProjectId().equals(projectId))
                .filter(i -> scopes.contains(i.getSource()))
                .filter(i -> i.getStatus() == IssueStatus.OPEN || i.getStatus() == IssueStatus.IN_PROGRESS)
                .filter(i -> {
                    Set<String> seen = seenBySource.get(i.getSource());
                    return seen == null || !seen.contains(i.getExternalId());
                })
                .toList();
        for (Issue gone : open) {
            gone.setStatus(IssueStatus.FIXED_EXTERNALLY);
            gone.setUpdatedAt(now);
            issueRepo.save(gone);
            resolvedExternally++;
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("source", source.name());
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("resolved", resolvedExternally);
        summary.put("total", raw.size());
        auditService.record(actor, AuditAction.SCAN_COMPLETED, ResourceType.PROJECT,
                String.valueOf(projectId), projectId, null, summary, null);

        log.info("Scan complete project={} source={} created={} updated={} resolved={}",
                projectId, source, created, updated, resolvedExternally);

        metrics.recordScanDuration(source, System.currentTimeMillis() - startMillis);
        return new IngestionResult(projectId, source.name(), created, updated, resolvedExternally, raw.size());
    }

    private IssueSourceConnector connectorFor(IssueSource source) {
        return connectors.stream()
                .filter(c -> c.source() == source)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No connector registered for source " + source));
    }

    private Issue toEntity(RawIssue r, long projectId, Instant now) {
        IssueStatus initialStatus = r.getRecommendedInitialStatus() != null
                ? r.getRecommendedInitialStatus()
                : IssueStatus.OPEN;
        return Issue.builder()
                .projectId(projectId)
                .source(r.getSource())
                .externalId(r.getExternalId())
                .severity(r.getSeverity())
                .status(initialStatus)
                .wontFixReason(initialStatus == IssueStatus.WONT_FIX ? r.getRecommendedStatusReason() : null)
                .title(r.getTitle())
                .description(r.getDescription())
                .filePath(r.getFilePath())
                .lineNumber(r.getLineNumber())
                .cwe(r.getCwe())
                .cve(r.getCve())
                .componentName(r.getComponentName())
                .currentVersion(r.getCurrentVersion())
                .safeVersion(r.getSafeVersion())
                .isTransitive(r.getTransitive())
                .transitivePath(r.getTransitivePath())
                .rawPayload(r.getRawPayload())
                .firstSeen(now)
                .lastSeen(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void applyUpdates(Issue existing, RawIssue r, Instant now) {
        existing.setSeverity(r.getSeverity());
        if (r.getTitle() != null) existing.setTitle(r.getTitle());
        if (r.getDescription() != null) existing.setDescription(r.getDescription());
        if (r.getSafeVersion() != null) existing.setSafeVersion(r.getSafeVersion());
        if (r.getCurrentVersion() != null) existing.setCurrentVersion(r.getCurrentVersion());
        existing.setRawPayload(r.getRawPayload());
        existing.setLastSeen(now);
        existing.setUpdatedAt(now);
        // If it was previously marked FIXED_EXTERNALLY but upstream is showing it again → reopen.
        if (existing.getStatus() == IssueStatus.FIXED_EXTERNALLY) {
            existing.setStatus(IssueStatus.OPEN);
        }
    }
}
