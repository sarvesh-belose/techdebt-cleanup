package com.enterprise.techdebt.orchestrator;

import com.enterprise.techdebt.automode.AutoModeGateService;
import com.enterprise.techdebt.azuredevops.AzureDevOpsClient;
import com.enterprise.techdebt.azuredevops.PullRequestRef;
import com.enterprise.techdebt.build.BuildResult;
import com.enterprise.techdebt.build.FastBuildRunner;
import com.enterprise.techdebt.copilot.FixGenerator;
import com.enterprise.techdebt.copilot.FixRequest;
import com.enterprise.techdebt.copilot.FixResult;
import com.enterprise.techdebt.domain.BuildOutcome;
import com.enterprise.techdebt.domain.FixAttempt;
import com.enterprise.techdebt.domain.FixRun;
import com.enterprise.techdebt.domain.FixRunState;
import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.Project;
import com.enterprise.techdebt.maven.PomFixResult;
import com.enterprise.techdebt.maven.PomFixer;
import com.enterprise.techdebt.notification.NotificationEventType;
import com.enterprise.techdebt.notification.NotificationMessage;
import com.enterprise.techdebt.notification.NotificationService;
import com.enterprise.techdebt.repo.FixAttemptRepository;
import com.enterprise.techdebt.repo.IssueRepository;
import com.enterprise.techdebt.repo.ProjectRepository;
import com.enterprise.techdebt.safety.FileChange;
import com.enterprise.techdebt.safety.SafetyAssessment;
import com.enterprise.techdebt.safety.SafetyGuard;
import com.enterprise.techdebt.security.CurrentUser;
import com.enterprise.techdebt.workspace.RepoWorkspaceManager;
import com.enterprise.techdebt.workspace.Workspace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives a Fix Run forward through CLONING → FIXING. Build verification, safety guard,
 * and PR creation arrive in subsequent sprints; for now we stop after the diff is
 * committed and the run is paused on FAST_BUILD (Sprint 4 lifts that).
 *
 * SCA fixes use the deterministic {@link PomFixer}. Source-edit fixes (SAST / Sonar)
 * delegate to a {@link FixGenerator} (Copilot CLI in prod, no-op in dev).
 */
@Service
public class FixOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FixOrchestrator.class);

    private final FixRunService fixRunService;
    private final RepoWorkspaceManager workspaceManager;
    private final PomFixer pomFixer;
    private final FixGenerator fixGenerator;
    private final FastBuildRunner fastBuildRunner;
    private final AzureDevOpsClient azureDevOpsClient;
    private final AutoModeGateService autoModeGate;
    private final NotificationService notificationService;
    private final ProjectRepository projectRepo;
    private final IssueRepository issueRepo;
    private final FixAttemptRepository attemptRepo;
    private final SafetyGuard safetyGuard;
    private final ObjectMapper objectMapper;
    private final int maxRefixAttempts;

    public FixOrchestrator(FixRunService fixRunService,
                           RepoWorkspaceManager workspaceManager,
                           PomFixer pomFixer,
                           FixGenerator fixGenerator,
                           FastBuildRunner fastBuildRunner,
                           AzureDevOpsClient azureDevOpsClient,
                           AutoModeGateService autoModeGate,
                           NotificationService notificationService,
                           ProjectRepository projectRepo,
                           IssueRepository issueRepo,
                           FixAttemptRepository attemptRepo,
                           SafetyGuard safetyGuard,
                           ObjectMapper objectMapper,
                           @Value("${techdebt.orchestrator.max-refix-attempts:3}") int maxRefixAttempts) {
        this.fixRunService = fixRunService;
        this.workspaceManager = workspaceManager;
        this.pomFixer = pomFixer;
        this.fixGenerator = fixGenerator;
        this.fastBuildRunner = fastBuildRunner;
        this.azureDevOpsClient = azureDevOpsClient;
        this.autoModeGate = autoModeGate;
        this.notificationService = notificationService;
        this.projectRepo = projectRepo;
        this.issueRepo = issueRepo;
        this.attemptRepo = attemptRepo;
        this.safetyGuard = safetyGuard;
        this.objectMapper = objectMapper;
        this.maxRefixAttempts = maxRefixAttempts;
    }

    public int getMaxRefixAttempts() {
        return maxRefixAttempts;
    }

    @Async("techdebtAsyncExecutor")
    public void executeAsync(CurrentUser actor, long runId) {
        execute(actor, runId);
    }

    /**
     * Synchronous core — exposed for tests and for callers (like
     * {@link com.enterprise.techdebt.scheduler.ScheduledScanRunner}) that already run
     * off the request thread. NOT annotated {@code @Transactional}: each state
     * transition commits in its own short transaction via {@link FixRunService}, so a
     * crash mid-run leaves a coherent persisted state.
     */
    public FixRun execute(CurrentUser actor, long runId) {
        FixRun run = fixRunService.get(runId);
        Project project = projectRepo.findById(run.getProjectId())
                .orElseThrow(() -> new IllegalStateException("project missing for run " + runId));
        List<Issue> issues = loadIssues(run);
        if (issues.isEmpty()) {
            return fixRunService.transition(actor, runId, FixRunState.FAILED,
                    null, null, null, null, null, "Fix run has no issues attached");
        }

        // CLONING
        Workspace workspace = null;
        try {
            String branchName = RepoWorkspaceManager.branchNameFor(
                    runId,
                    issues.get(0).getSource().name(),
                    shortDescFor(issues));
            fixRunService.transition(actor, runId, FixRunState.CLONING,
                    branchName, null, null, null, null, null);
            workspace = workspaceManager.cloneRepo(
                    runId, project.getAzureRepoUrl(), project.getDefaultBranch(), branchName);

            // FIXING + FAST_BUILD with refix loop. Up to `maxRefixAttempts` total tries.
            String previousBuildLog = null;
            String lastCommitSha = null;
            BuildResult lastBuild = null;
            Integer lastSafetyScore = null;

            for (int attemptNumber = 1; attemptNumber <= maxRefixAttempts; attemptNumber++) {
                if (attemptNumber > 1) {
                    // Refix attempt: discard the failed commit and try again with build error context.
                    workspaceManager.resetToBase(workspace);
                }

                fixRunService.transition(actor, runId, FixRunState.FIXING);
                FixAttempt attempt = beginAttempt(runId);

                ApplyOutcome outcome = applyFixes(workspace, issues, run, previousBuildLog, attemptNumber);
                attempt.setDiff(outcome.diff);

                if (!outcome.anyChanges) {
                    attempt.setCompletedAt(Instant.now());
                    attemptRepo.save(attempt);
                    if (workspace != null) workspaceManager.cleanup(workspace);
                    return fixRunService.transition(actor, runId, FixRunState.FAILED,
                            null, null, null, null, null,
                            "Attempt " + attemptNumber + " produced no changes. " + outcome.notes);
                }

                // Safety Guard runs BEFORE commit so a blocked diff never enters history.
                List<FileChange> changes = captureChanges(workspace);
                SafetyAssessment assessment = safetyGuard.assess(changes, project.getSafetyThreshold());
                attempt.setSafetyScore(assessment.getTotalScore());
                attempt.setSafetySignalsJson(toJson(assessment));
                lastSafetyScore = assessment.getTotalScore();

                if (assessment.isBlocked()) {
                    attempt.setCompletedAt(Instant.now());
                    attemptRepo.save(attempt);
                    if (workspace != null) workspaceManager.cleanup(workspace);
                    log.warn("Run {} blocked by Safety Guard: {}", runId, assessment.summary());
                    FixRun blocked = fixRunService.transition(actor, runId, FixRunState.SAFETY_BLOCKED,
                            workspace.fixBranch(), null, null,
                            assessment.getTotalScore(), assessment.summary(), null);
                    notifySafe(NotificationMessage.builder()
                            .eventType(NotificationEventType.SAFETY_BLOCKED)
                            .severity(com.enterprise.techdebt.domain.Severity.HIGH)
                            .projectId(run.getProjectId())
                            .fixRunId(runId)
                            .userId(run.getInitiatedByUserId())
                            .title("Fix run #" + runId + " blocked by Safety Guard")
                            .body(assessment.summary())
                            .deeplink("/projects/" + run.getProjectId() + "/runs/" + runId)
                            .build());
                    return blocked;
                }

                String commitMessage = buildCommitMessage(run, issues, outcome, attemptNumber);
                String commitSha = workspaceManager.commit(workspace, commitMessage);
                lastCommitSha = commitSha;
                log.info("Run {} attempt {} committed {} to branch {}",
                        runId, attemptNumber, commitSha, workspace.fixBranch());

                // FAST_BUILD
                fixRunService.transition(actor, runId, FixRunState.FAST_BUILD,
                        workspace.fixBranch(), commitSha, null,
                        assessment.getTotalScore(), null, null);
                lastBuild = fastBuildRunner.run(workspace);
                attempt.setBuildOutcome(lastBuild.getOutcome());
                attempt.setBuildLogExcerpt(lastBuild.getLogExcerpt());
                attempt.setCompletedAt(Instant.now());
                attemptRepo.save(attempt);
                log.info("Run {} attempt {} build outcome={} duration={}ms",
                        runId, attemptNumber, lastBuild.getOutcome(), lastBuild.getDurationMs());

                if (lastBuild.getOutcome() == BuildOutcome.SUCCESS) {
                    return openPullRequest(actor, runId, run, issues, workspace,
                            commitSha, assessment.getTotalScore());
                }

                // Build failed — set up refix iteration if attempts remain.
                previousBuildLog = lastBuild.getLogExcerpt();
                if (attemptNumber >= maxRefixAttempts) {
                    if (workspace != null) workspaceManager.cleanup(workspace);
                    FixRun failed = fixRunService.transition(actor, runId, FixRunState.FAILED,
                            workspace.fixBranch(), lastCommitSha, null,
                            lastSafetyScore, null,
                            "Build failed after " + maxRefixAttempts + " attempt(s): "
                                    + lastBuild.getFailureSummary());
                    notifySafe(NotificationMessage.builder()
                            .eventType(NotificationEventType.RUN_FAILED)
                            .severity(com.enterprise.techdebt.domain.Severity.HIGH)
                            .projectId(run.getProjectId())
                            .fixRunId(runId)
                            .userId(run.getInitiatedByUserId())
                            .title("Fix run #" + runId + " failed after " + maxRefixAttempts + " attempts")
                            .body(lastBuild.getFailureSummary())
                            .deeplink("/projects/" + run.getProjectId() + "/runs/" + runId)
                            .build());
                    return failed;
                }
            }
            // Defensive: loop always returns before falling through.
            throw new IllegalStateException("Refix loop exited without a terminal state");
        } catch (RuntimeException e) {
            log.error("Fix run {} failed: {}", runId, e.getMessage(), e);
            if (workspace != null) {
                workspaceManager.cleanup(workspace);
            }
            return fixRunService.transition(actor, runId, FixRunState.FAILED,
                    null, null, null, null, null, truncate(e.getMessage(), 4000));
        }
    }

    private FixRun openPullRequest(CurrentUser actor, long runId, FixRun run, List<Issue> issues,
                                    Workspace workspace, String commitSha, Integer safetyScore) {
        // CANONICAL_BUILD: Sprint 6 still triggers real Azure Pipelines from the REST
        // client (skeleton today); the orchestrator passes through after fast-tier success.
        fixRunService.transition(actor, runId, FixRunState.CANONICAL_BUILD,
                workspace.fixBranch(), commitSha, null, safetyScore, null, null);

        azureDevOpsClient.pushFixBranch(workspace);
        String title = "techdebt: " + issues.size() + " issue(s) — run " + runId;
        String description = renderPrDescription(run, issues, safetyScore);
        PullRequestRef pr = azureDevOpsClient.openPullRequest(workspace, title, description,
                List.of() /* required reviewers — wired in Sprint 6 from project config */);

        log.info("Run {} PR opened {}", runId, pr.url());
        FixRun prOpened = fixRunService.transition(actor, runId, FixRunState.PR_OPENED,
                workspace.fixBranch(), commitSha, pr.url(), safetyScore, null, null);
        notifySafe(NotificationMessage.builder()
                .eventType(NotificationEventType.PR_OPENED)
                .severity(com.enterprise.techdebt.domain.Severity.INFO)
                .projectId(run.getProjectId())
                .fixRunId(runId)
                .userId(run.getInitiatedByUserId())
                .title("PR opened for fix run #" + runId)
                .body(pr.url())
                .deeplink(pr.url())
                .build());

        // L4 — Auto-merge gate. Only fires when the run was started in auto mode AND
        // the project policy + rate limit + global kill switch all permit it.
        if (run.isAutoMode()) {
            Project project = projectRepo.findById(run.getProjectId()).orElse(null);
            if (project != null) {
                AutoModeGateService.GateResult gate = autoModeGate.evaluate(project, issues);
                if (gate.allowed()) {
                    try {
                        azureDevOpsClient.mergePullRequest(pr);
                        log.info("Run {} auto-merged PR {}", runId, pr.url());
                        FixRun merged = fixRunService.transition(actor, runId, FixRunState.MERGED,
                                workspace.fixBranch(), commitSha, pr.url(), safetyScore, null, null);
                        notifySafe(NotificationMessage.builder()
                                .eventType(NotificationEventType.MERGED)
                                .severity(com.enterprise.techdebt.domain.Severity.INFO)
                                .projectId(run.getProjectId())
                                .fixRunId(runId)
                                .userId(run.getInitiatedByUserId())
                                .title("Fix run #" + runId + " auto-merged")
                                .body(pr.url())
                                .deeplink(pr.url())
                                .build());
                        return merged;
                    } catch (RuntimeException ex) {
                        log.warn("Run {} auto-merge failed, PR remains open: {}", runId, ex.getMessage());
                    }
                } else {
                    log.info("Run {} auto-merge skipped: {}", runId, gate.reason());
                }
            }
        }
        return prOpened;
    }

    private String renderPrDescription(FixRun run, List<Issue> issues, Integer safetyScore) {
        StringBuilder b = new StringBuilder();
        b.append("Generated by TechDebt Cleanup platform · run #").append(run.getId()).append("\n\n");
        b.append("**Issues fixed:**\n");
        for (Issue i : issues) {
            b.append("- `").append(i.getSource()).append("` ").append(i.getSeverity())
                    .append(": ").append(i.getTitle());
            if (i.getCve() != null) b.append(" (").append(i.getCve()).append(")");
            if (i.getCwe() != null) b.append(" (CWE-").append(i.getCwe()).append(")");
            b.append(" — issue #").append(i.getId()).append("\n");
        }
        if (safetyScore != null) {
            b.append("\n**Safety score:** ").append(safetyScore).append("/100\n");
        }
        b.append("\n_Auto-merge requires green canonical pipeline + safety pass + required reviewer approval._\n");
        return b.toString();
    }

    private ApplyOutcome applyFixes(Workspace workspace, List<Issue> issues, FixRun run,
                                     String previousBuildLog, int attemptNumber) {
        ApplyOutcome outcome = new ApplyOutcome();
        List<Issue> sourceFixIssues = new ArrayList<>();
        java.nio.file.Path rootPom = workspace.path().resolve("pom.xml");

        for (Issue issue : issues) {
            if (issue.getSource() == IssueSource.VERACODE_SCA && isPomFixable(issue)) {
                String[] coord = splitCoordinate(issue.getComponentName());
                if (coord == null) {
                    outcome.notes.append("skipped issue ").append(issue.getId())
                            .append(" (cannot parse coordinate)\n");
                    continue;
                }
                PomFixResult result;
                if (Boolean.TRUE.equals(issue.getIsTransitive())) {
                    // G4 — transitive dependency: pin via dependencyManagement on the root pom.
                    result = pomFixer.overrideTransitive(rootPom, coord[0], coord[1],
                            issue.getSafeVersion(), issue.getCve());
                } else {
                    // G6 — multi-module: locate the module pom that declares the dep, fall back to root.
                    java.nio.file.Path targetPom = pomFixer
                            .findOwningModule(rootPom, coord[0], coord[1])
                            .orElse(rootPom);
                    result = pomFixer.bumpDirectDependency(
                            targetPom, coord[0], coord[1], issue.getSafeVersion());
                    if (!result.isChanged()) {
                        // Direct bump didn't apply — try a dependencyManagement override on the root.
                        // Common reason: the version is inherited from a parent BOM not declared here.
                        result = pomFixer.overrideTransitive(rootPom, coord[0], coord[1],
                                issue.getSafeVersion(), issue.getCve());
                    }
                }
                if (result.isChanged()) {
                    outcome.anyChanges = true;
                    outcome.notes.append("[").append(result.getMechanism()).append("] ")
                            .append(coord[0]).append(":").append(coord[1])
                            .append(" ").append(result.getPreviousVersion())
                            .append(" → ").append(result.getNewVersion()).append("\n");
                } else {
                    outcome.notes.append("no-op SCA fix for ").append(coord[0]).append(":")
                            .append(coord[1]).append(" — ").append(result.getNotes()).append("\n");
                }
            } else {
                // SAST / SonarQube / JUnit coverage → goes through the LLM-backed generator.
                sourceFixIssues.add(issue);
            }
        }

        if (!sourceFixIssues.isEmpty()) {
            FixResult result = fixGenerator.generate(FixRequest.builder()
                    .runId(run.getId())
                    .workspacePath(workspace.path())
                    .issues(sourceFixIssues)
                    .previousBuildError(previousBuildLog)
                    .attemptNumber(attemptNumber)
                    .build());
            if (result.isChanged()) {
                outcome.anyChanges = true;
                outcome.notes.append("generator=").append(result.getGenerator())
                        .append(" applied diff over ").append(sourceFixIssues.size()).append(" issues\n");
            } else {
                outcome.notes.append("generator=").append(result.getGenerator())
                        .append(" produced no change — ").append(nullSafe(result.getExplanation())).append("\n");
            }
            outcome.diff = result.getDiff();
        }
        return outcome;
    }

    private List<FileChange> captureChanges(Workspace workspace) {
        List<String> paths = workspaceManager.changedFiles(workspace);
        List<FileChange> changes = new ArrayList<>(paths.size());
        for (String path : paths) {
            String before = workspaceManager.contentAtHead(workspace, path);
            String after = workspaceManager.contentInWorkingTree(workspace, path);
            changes.add(new FileChange(path, before, after));
        }
        return changes;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    private void notifySafe(NotificationMessage msg) {
        try {
            notificationService.notify(msg);
        } catch (RuntimeException ex) {
            log.warn("Notification dispatch failed for {}: {}", msg.getEventType(), ex.getMessage());
        }
    }

    private FixAttempt beginAttempt(long runId) {
        int next = attemptRepo.findAllByFixRunIdOrderByAttemptNumberAsc(runId).size() + 1;
        return attemptRepo.save(FixAttempt.builder()
                .fixRunId(runId)
                .attemptNumber(next)
                .startedAt(Instant.now())
                .build());
    }

    private List<Issue> loadIssues(FixRun run) {
        String json = run.getIssueIdsJson();
        if (json == null || json.isBlank()) return List.of();
        List<Long> ids = parseIssueIds(json);
        List<Issue> issues = new ArrayList<>(ids.size());
        for (Long id : ids) {
            issueRepo.findById(id).ifPresent(issues::add);
        }
        return issues;
    }

    private List<Long> parseIssueIds(String json) {
        // The runner stores issueIds as a JSON array of longs. Tiny, safe to parse here
        // without pulling Jackson into this hot path.
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isBlank()) return List.of();
        List<Long> out = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            try {
                out.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip non-numeric entries silently
            }
        }
        return out;
    }

    private boolean isPomFixable(Issue issue) {
        // Both direct (bump) and transitive (dependencyManagement override) SCA fixes are
        // routed through PomFixer. We only fall back to the LLM generator for non-SCA
        // findings, or SCA findings without a safe version.
        return issue.getSafeVersion() != null && issue.getComponentName() != null;
    }

    private String[] splitCoordinate(String component) {
        if (component == null) return null;
        int idx = component.indexOf(':');
        if (idx <= 0 || idx == component.length() - 1) return null;
        return new String[] { component.substring(0, idx).trim(),
                              component.substring(idx + 1).trim() };
    }

    private String buildCommitMessage(FixRun run, List<Issue> issues, ApplyOutcome outcome,
                                       int attemptNumber) {
        StringBuilder b = new StringBuilder();
        b.append("techdebt: fix run ").append(run.getId());
        if (attemptNumber > 1) b.append(" (refix #").append(attemptNumber).append(")");
        b.append(" — ").append(issues.size()).append(" issue(s)\n\n");
        for (Issue i : issues) {
            b.append("- ").append(i.getSource()).append(" / ").append(i.getSeverity())
                    .append(": ").append(i.getTitle()).append(" (#").append(i.getId()).append(")\n");
        }
        b.append("\nDetails:\n").append(outcome.notes);
        return truncate(b.toString(), 4000);
    }

    private String shortDescFor(List<Issue> issues) {
        Issue first = issues.get(0);
        if (first.getComponentName() != null) return first.getComponentName();
        if (first.getCve() != null) return first.getCve();
        if (first.getTitle() != null) return first.getTitle();
        return "fix";
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static class ApplyOutcome {
        boolean anyChanges;
        String diff;
        final StringBuilder notes = new StringBuilder();
    }
}
