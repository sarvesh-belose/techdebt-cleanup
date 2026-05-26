package com.enterprise.techdebt.orchestrator;

import com.enterprise.techdebt.api.dto.CreateProjectRequest;
import com.enterprise.techdebt.build.BuildExecutor;
import com.enterprise.techdebt.build.BuildResult;
import com.enterprise.techdebt.copilot.FixGenerator;
import com.enterprise.techdebt.copilot.FixRequest;
import com.enterprise.techdebt.copilot.FixResult;
import com.enterprise.techdebt.domain.BuildOutcome;
import com.enterprise.techdebt.domain.FixRun;
import com.enterprise.techdebt.domain.FixRunState;
import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.IssueStatus;
import com.enterprise.techdebt.domain.Project;
import com.enterprise.techdebt.domain.Severity;
import com.enterprise.techdebt.repo.AppUserRepository;
import com.enterprise.techdebt.repo.FixAttemptRepository;
import com.enterprise.techdebt.repo.FixRunRepository;
import com.enterprise.techdebt.repo.IssueRepository;
import com.enterprise.techdebt.repo.NotificationRepository;
import com.enterprise.techdebt.repo.ProjectPermissionRepository;
import com.enterprise.techdebt.repo.ProjectRepository;
import com.enterprise.techdebt.repo.ScanSourceConfigRepository;
import com.enterprise.techdebt.security.CurrentUserService;
import com.enterprise.techdebt.service.ProjectService;
import com.enterprise.techdebt.workspace.RepoWorkspaceManager;
import com.enterprise.techdebt.workspace.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end orchestrator test. Real beans: FixRunService + state machine, SafetyGuard
 * (real signals + real metrics), audit, notifications, AutoModeGateService.
 * Stubbed: RepoWorkspaceManager (no git CLI needed), BuildExecutor (canned BUILD SUCCESS),
 * FixGenerator (claims to have produced a diff). Azure DevOps stays Noop by default.
 *
 * Covers the happy path: CLONING → FIXING → safety pass → FAST_BUILD success →
 * CANONICAL_BUILD → PR_OPENED. Failure paths are covered by unit tests on the
 * state machine; this test is about wiring across the seams.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(FixOrchestratorE2ETest.Fixtures.class)
class FixOrchestratorE2ETest {

    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        RepoWorkspaceManager workspaceManager() {
            RepoWorkspaceManager mock = mock(RepoWorkspaceManager.class);
            Workspace ws = new Workspace(1L, Path.of(System.getProperty("java.io.tmpdir"), "tdc-e2e"),
                    "https://dev.azure.com/acme/banking/_git/e2e", "main", "techdebt/run-1-sast-test");
            when(mock.cloneRepo(anyLong(), anyString(), anyString(), anyString())).thenReturn(ws);
            when(mock.commit(any(), anyString())).thenReturn("deadbeefcafebabedeadbeefcafebabe00000000");
            // No changed files = safety guard score 0 = pass.
            when(mock.changedFiles(any())).thenReturn(List.of());
            when(mock.contentAtHead(any(), anyString())).thenReturn(null);
            when(mock.contentInWorkingTree(any(), anyString())).thenReturn(null);
            return mock;
        }

        @Bean
        @Primary
        BuildExecutor buildExecutor() {
            return (path, args, timeoutSeconds) -> BuildResult.builder()
                    .outcome(BuildOutcome.SUCCESS)
                    .exitCode(0)
                    .durationMs(123L)
                    .logExcerpt("BUILD SUCCESS\n")
                    .failureSummary(null)
                    .build();
        }

        @Bean
        @Primary
        FixGenerator fakeGenerator() {
            return new FixGenerator() {
                @Override public String name() { return "fake-e2e"; }
                @Override public FixResult generate(FixRequest request) {
                    return FixResult.builder()
                            .changed(true)
                            .diff("diff --git a/Foo.java b/Foo.java\n+ // patched\n")
                            .explanation("e2e fixture")
                            .generator(name())
                            .build();
                }
            };
        }
    }

    @Autowired FixOrchestrator orchestrator;
    @Autowired FixRunService fixRunService;
    @Autowired ProjectService projectService;
    @Autowired CurrentUserService currentUserService;
    @Autowired ProjectRepository projectRepo;
    @Autowired ProjectPermissionRepository permissionRepo;
    @Autowired IssueRepository issueRepo;
    @Autowired FixRunRepository fixRunRepo;
    @Autowired FixAttemptRepository attemptRepo;
    @Autowired ScanSourceConfigRepository scanCfgRepo;
    @Autowired NotificationRepository notificationRepo;
    @Autowired AppUserRepository userRepo;

    private long projectId;
    private long issueId;

    @BeforeEach
    void seed() {
        notificationRepo.deleteAll();
        attemptRepo.deleteAll();
        fixRunRepo.deleteAll();
        issueRepo.deleteAll();
        scanCfgRepo.deleteAll();
        permissionRepo.deleteAll();
        projectRepo.deleteAll();
        userRepo.deleteAll();

        var actor = currentUserService.require();
        Project p = projectService.create(actor, new CreateProjectRequest(
                "e2e-svc", null,
                "https://dev.azure.com/acme/banking/_git/e2e", "main"));
        projectId = p.getId();

        Instant now = Instant.now();
        Issue i = issueRepo.save(Issue.builder()
                .projectId(projectId)
                .source(IssueSource.VERACODE_SAST)
                .externalId("SAST-1")
                .severity(Severity.HIGH)
                .status(IssueStatus.OPEN)
                .title("XSS in render")
                .cwe("CWE-79")
                .filePath("src/main/java/Foo.java")
                .lineNumber(42)
                .firstSeen(now)
                .lastSeen(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
        issueId = i.getId();
    }

    @Test
    void happyPathReachesPrOpened() {
        var actor = currentUserService.require();
        FixRun run = fixRunService.create(actor, projectId, List.of(issueId), false);

        FixRun finalRun = orchestrator.execute(actor, run.getId());

        assertThat(finalRun.getState()).isEqualTo(FixRunState.PR_OPENED);
        assertThat(finalRun.getBranchName()).startsWith("techdebt/run-");
        assertThat(finalRun.getCommitSha()).hasSize(40);
        assertThat(finalRun.getPrUrl()).contains("pullrequest/noop-");
        assertThat(finalRun.getSafetyScore()).isNotNull();

        // Attempt row recorded with SUCCESS build outcome.
        var attempts = attemptRepo.findAllByFixRunIdOrderByAttemptNumberAsc(run.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getBuildOutcome()).isEqualTo(BuildOutcome.SUCCESS);
        assertThat(attempts.get(0).getCompletedAt()).isNotNull();

        // Notification fired for PR_OPENED.
        assertThat(notificationRepo.findAll())
                .anyMatch(n -> n.getEventType().name().equals("PR_OPENED")
                        && n.getFixRunId() != null && n.getFixRunId().equals(run.getId()));
    }

    @Test
    void noIssuesAttachedFails() {
        var actor = currentUserService.require();
        FixRun run = fixRunService.create(actor, projectId, List.of(), false);

        FixRun finalRun = orchestrator.execute(actor, run.getId());

        assertThat(finalRun.getState()).isEqualTo(FixRunState.FAILED);
        assertThat(finalRun.getFailureReason()).contains("no issues attached");
    }
}
