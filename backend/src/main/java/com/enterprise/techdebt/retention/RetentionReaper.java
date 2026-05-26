package com.enterprise.techdebt.retention;

import com.enterprise.techdebt.audit.AuditAction;
import com.enterprise.techdebt.audit.AuditService;
import com.enterprise.techdebt.audit.ResourceType;
import com.enterprise.techdebt.domain.FixAttempt;
import com.enterprise.techdebt.domain.FixRun;
import com.enterprise.techdebt.domain.FixRunState;
import com.enterprise.techdebt.domain.Notification;
import com.enterprise.techdebt.domain.Project;
import com.enterprise.techdebt.repo.FixAttemptRepository;
import com.enterprise.techdebt.repo.FixRunRepository;
import com.enterprise.techdebt.repo.NotificationRepository;
import com.enterprise.techdebt.repo.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-project retention reaper. Runs daily and removes:
 *   - terminal fix runs (MERGED, FAILED, ABORTED) older than {@code project.retention_days}
 *   - their fix_attempt children (cascade via explicit delete)
 *   - read notifications scoped to the project older than the same window
 *
 * Audit events and currently-active fix runs are NEVER deleted. The reaper itself writes
 * one audit event per project describing what it removed, so InfoSec can reconstruct the
 * deletion history even after the underlying rows are gone.
 *
 * Cron defaults to 03:00 UTC daily. Skips the run entirely if {@code techdebt.retention.enabled=false}.
 */
@Component
public class RetentionReaper {

    private static final Logger log = LoggerFactory.getLogger(RetentionReaper.class);

    private static final Set<FixRunState> TERMINAL = EnumSet.of(
            FixRunState.MERGED, FixRunState.FAILED, FixRunState.ABORTED);

    private final ProjectRepository projectRepo;
    private final FixRunRepository fixRunRepo;
    private final FixAttemptRepository attemptRepo;
    private final NotificationRepository notificationRepo;
    private final AuditService auditService;
    private final boolean enabled;

    public RetentionReaper(ProjectRepository projectRepo,
                            FixRunRepository fixRunRepo,
                            FixAttemptRepository attemptRepo,
                            NotificationRepository notificationRepo,
                            AuditService auditService,
                            @Value("${techdebt.retention.enabled:true}") boolean enabled) {
        this.projectRepo = projectRepo;
        this.fixRunRepo = fixRunRepo;
        this.attemptRepo = attemptRepo;
        this.notificationRepo = notificationRepo;
        this.auditService = auditService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${techdebt.retention.cron:0 0 3 * * *}")
    @Transactional
    public void reap() {
        if (!enabled) {
            log.debug("Retention reaper disabled");
            return;
        }
        ReapTotals overall = new ReapTotals();
        for (Project p : projectRepo.findAll()) {
            ReapTotals projectTotals = reapProject(p);
            overall.add(projectTotals);
        }
        log.info("Retention reap complete: {} fix-runs, {} attempts, {} notifications removed across {} projects",
                overall.runs, overall.attempts, overall.notifications, overall.projects);
    }

    @Transactional
    public ReapTotals reapProject(Project project) {
        ReapTotals totals = new ReapTotals();
        if (project.getRetentionDays() <= 0) return totals;

        Instant cutoff = Instant.now().minus(project.getRetentionDays(), ChronoUnit.DAYS);

        List<FixRun> terminalOld = fixRunRepo.findAll().stream()
                .filter(r -> r.getProjectId() != null && r.getProjectId().equals(project.getId()))
                .filter(r -> TERMINAL.contains(r.getState()))
                .filter(r -> {
                    Instant when = r.getCompletedAt() != null ? r.getCompletedAt() : r.getUpdatedAt();
                    return when != null && when.isBefore(cutoff);
                })
                .toList();

        for (FixRun r : terminalOld) {
            List<FixAttempt> attempts = attemptRepo.findAllByFixRunIdOrderByAttemptNumberAsc(r.getId());
            attemptRepo.deleteAll(attempts);
            totals.attempts += attempts.size();
            fixRunRepo.delete(r);
            totals.runs++;
        }

        List<Notification> readOld = notificationRepo.findAll().stream()
                .filter(n -> n.getProjectId() != null && n.getProjectId().equals(project.getId()))
                .filter(n -> n.getReadAt() != null && n.getReadAt().isBefore(cutoff))
                .toList();
        notificationRepo.deleteAll(readOld);
        totals.notifications = readOld.size();
        totals.projects = 1;

        if (totals.runs > 0 || totals.attempts > 0 || totals.notifications > 0) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("retentionDays", project.getRetentionDays());
            summary.put("cutoff", cutoff.toString());
            summary.put("fixRunsDeleted", totals.runs);
            summary.put("attemptsDeleted", totals.attempts);
            summary.put("notificationsDeleted", totals.notifications);
            auditService.record(null, AuditAction.PROJECT_UPDATED, ResourceType.PROJECT,
                    String.valueOf(project.getId()), project.getId(),
                    null, summary, "retention reaper ran");
        }
        return totals;
    }

    public static class ReapTotals {
        public int projects;
        public int runs;
        public int attempts;
        public int notifications;

        public void add(ReapTotals other) {
            this.projects += other.projects;
            this.runs += other.runs;
            this.attempts += other.attempts;
            this.notifications += other.notifications;
        }

        public Map<String, Integer> asMap() {
            Map<String, Integer> m = new HashMap<>();
            m.put("projects", projects);
            m.put("runs", runs);
            m.put("attempts", attempts);
            m.put("notifications", notifications);
            return m;
        }
    }
}
