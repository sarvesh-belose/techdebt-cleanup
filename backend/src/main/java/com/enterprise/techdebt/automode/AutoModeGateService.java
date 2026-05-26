package com.enterprise.techdebt.automode;

import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.Project;
import com.enterprise.techdebt.repo.FixRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * One stop for the question "should this auto-mode action proceed?". Three gates:
 *
 *   1. global kill switch  — if flipped, no auto runs anywhere, anytime
 *   2. per-project rate limit — auto_rate_limit_per_hour
 *   3. per-source severity policy — auto_policy_json must allow every issue in the run
 *
 * The orchestrator consults this before auto-merging a PR. The scheduled-scan
 * job consults this before kicking off any auto-mode fix run.
 */
@Service
public class AutoModeGateService {

    private static final Logger log = LoggerFactory.getLogger(AutoModeGateService.class);

    private final SystemSettingsService systemSettings;
    private final FixRunRepository fixRunRepository;
    private final ObjectMapper objectMapper;

    public AutoModeGateService(SystemSettingsService systemSettings,
                                FixRunRepository fixRunRepository,
                                ObjectMapper objectMapper) {
        this.systemSettings = systemSettings;
        this.fixRunRepository = fixRunRepository;
        this.objectMapper = objectMapper;
    }

    public GateResult evaluate(Project project, List<Issue> issues) {
        if (systemSettings.isAutoModeKilled()) {
            return GateResult.blocked("global auto-mode kill switch is on");
        }
        if (!project.isAutoModeEnabled()) {
            return GateResult.blocked("project auto-mode is off");
        }
        AutoModePolicy policy = AutoModePolicy.fromJson(project.getAutoPolicyJson(), objectMapper);
        if (policy.isEmpty()) {
            return GateResult.blocked("project auto-policy is empty (deny by default)");
        }
        for (Issue i : issues) {
            if (!policy.isEligible(i.getSource(), i.getSeverity())) {
                return GateResult.blocked("issue #" + i.getId() + " (" + i.getSource()
                        + "/" + i.getSeverity() + ") is not eligible under auto-policy");
            }
        }
        if (project.getAutoRateLimitPerHour() > 0) {
            int recentAutoRuns = countAutoRunsInLastHour(project.getId());
            if (recentAutoRuns >= project.getAutoRateLimitPerHour()) {
                return GateResult.blocked("auto-rate limit reached: "
                        + recentAutoRuns + "/" + project.getAutoRateLimitPerHour() + " per hour");
            }
        }
        return GateResult.pass();
    }

    private int countAutoRunsInLastHour(long projectId) {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        long count = fixRunRepository.countAutoRunsSince(projectId, cutoff);
        log.debug("Auto runs for project {} in last hour: {}", projectId, count);
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    public record GateResult(boolean allowed, String reason) {
        public static GateResult pass() { return new GateResult(true, null); }
        public static GateResult blocked(String reason) { return new GateResult(false, reason); }
    }
}
