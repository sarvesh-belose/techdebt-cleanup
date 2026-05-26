package com.enterprise.techdebt.orchestrator;

import com.enterprise.techdebt.audit.AuditAction;
import com.enterprise.techdebt.audit.AuditService;
import com.enterprise.techdebt.audit.ResourceType;
import com.enterprise.techdebt.config.MdcConstants;
import com.enterprise.techdebt.domain.FixRun;
import com.enterprise.techdebt.domain.FixRunState;
import com.enterprise.techdebt.metrics.PlatformMetrics;
import com.enterprise.techdebt.repo.FixRunRepository;
import com.enterprise.techdebt.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Owns Fix Run persistence and enforces the state machine. The orchestrator drives
 * transitions; this service is the only writer to fix_run state.
 */
@Service
public class FixRunService {

    private final FixRunRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PlatformMetrics metrics;

    public FixRunService(FixRunRepository repository, AuditService auditService,
                         ObjectMapper objectMapper, PlatformMetrics metrics) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional
    public FixRun create(CurrentUser actor, long projectId, List<Long> issueIds, boolean autoMode) {
        Instant now = Instant.now();
        FixRun run = FixRun.builder()
                .projectId(projectId)
                .initiatedByUserId(actor.userId())
                .autoMode(autoMode)
                .state(FixRunState.QUEUED)
                .issueIdsJson(toJson(issueIds))
                .createdAt(now)
                .updatedAt(now)
                .build();
        run = repository.save(run);
        MDC.put(MdcConstants.RUN_ID, String.valueOf(run.getId()));
        auditService.record(actor, AuditAction.FIX_RUN_CREATED, ResourceType.FIX_RUN,
                String.valueOf(run.getId()), projectId, null,
                Map.of("issueIds", issueIds, "autoMode", autoMode), null);
        return run;
    }

    @Transactional(readOnly = true)
    public FixRun get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Fix run not found: " + id));
    }

    @Transactional
    public FixRun transition(CurrentUser actor, long runId, FixRunState target,
                             String branchName, String commitSha, String prUrl,
                             Integer safetyScore, String safetyReason, String failureReason) {
        FixRun run = get(runId);
        FixRunState from = run.getState();
        FixRunStateMachine.requireTransition(from, target);

        Instant now = Instant.now();
        run.setState(target);
        run.setUpdatedAt(now);
        if (branchName != null) run.setBranchName(branchName);
        if (commitSha != null) run.setCommitSha(commitSha);
        if (prUrl != null) run.setPrUrl(prUrl);
        if (safetyScore != null) run.setSafetyScore(safetyScore);
        if (safetyReason != null) run.setSafetyBlockedReason(safetyReason);
        if (failureReason != null) run.setFailureReason(failureReason);
        if (FixRunStateMachine.isTerminal(target)) {
            run.setCompletedAt(now);
        }
        run = repository.save(run);
        metrics.recordFixRunState(target);
        if (target == FixRunState.SAFETY_BLOCKED) {
            metrics.recordSafetyDecision(true);
        }

        MDC.put(MdcConstants.RUN_ID, String.valueOf(run.getId()));
        // HashMap (not Map.of) — Map.of rejects null values and early transitions
        // legitimately carry null branchName/commitSha/prUrl.
        java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("state", target.name());
        after.put("branchName", run.getBranchName());
        after.put("commitSha", run.getCommitSha());
        after.put("prUrl", run.getPrUrl());
        auditService.record(actor, AuditAction.FIX_RUN_STATE_CHANGED, ResourceType.FIX_RUN,
                String.valueOf(run.getId()), run.getProjectId(),
                Map.of("state", from.name()),
                after,
                failureReason);
        return run;
    }

    /** Convenience for simple state-only transitions. */
    @Transactional
    public FixRun transition(CurrentUser actor, long runId, FixRunState target) {
        return transition(actor, runId, target, null, null, null, null, null, null);
    }

    @Transactional
    public FixRun abort(CurrentUser actor, long runId, String reason) {
        return transition(actor, runId, FixRunState.ABORTED,
                null, null, null, null, null, reason);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
