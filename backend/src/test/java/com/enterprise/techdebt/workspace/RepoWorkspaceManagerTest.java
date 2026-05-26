package com.enterprise.techdebt.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that actually exercise the git CLI. Skipped when git isn't on PATH (e.g. CI
 * containers without git) so the build stays green; the integration is otherwise too
 * easy to mis-mock to be worth faking.
 */
class RepoWorkspaceManagerTest {

    private final GitRunner gitRunner = new GitRunner();

    @BeforeEach
    void assumeGit() {
        try {
            gitRunner.run(null, "--version");
        } catch (GitException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "git not available on PATH");
        }
    }

    @Test
    void branchNameFollowsConvention() {
        String b = RepoWorkspaceManager.branchNameFor(42, "VERACODE_SCA", "log4j core 2.14");
        assertThat(b).isEqualTo("techdebt/run-42-veracode_sca-log4j-core-2-14");
    }

    @Test
    void branchNameTrimsLongSlugs() {
        String b = RepoWorkspaceManager.branchNameFor(7, "SAST",
                "Cross-site scripting in WebController#handleRequest somewhere deep");
        assertThat(b).startsWith("techdebt/run-7-sast-");
        // slug capped at 30 chars, exclusive of run/source prefix
        String slug = b.substring("techdebt/run-7-sast-".length());
        assertThat(slug.length()).isLessThanOrEqualTo(30);
    }

    @Test
    @DisabledIfSystemProperty(named = "skipGitTests", matches = "true")
    void cloneCreatesBranchAndAllowsCommit(@TempDir Path tmp) throws IOException {
        Path workspaceRoot = tmp.resolve("workspaces");
        Path origin = tmp.resolve("origin.git");
        Path seed = tmp.resolve("seed");
        Files.createDirectories(seed);
        Files.writeString(seed.resolve("README.md"), "hello\n");

        // Seed a bare repo with one commit on main.
        gitRunner.run(seed, "init", "-b", "main");
        gitRunner.run(seed, "config", "user.email", "test@local");
        gitRunner.run(seed, "config", "user.name", "test");
        gitRunner.run(seed, "add", "-A");
        gitRunner.run(seed, "commit", "-m", "initial");
        gitRunner.run(seed, "clone", "--bare", seed.toString(), origin.toString());

        RepoWorkspaceManager mgr = new RepoWorkspaceManager(
                gitRunner, workspaceRoot.toString(), "TechDebt Bot", "techdebt-bot@local");
        Workspace ws = mgr.cloneRepo(1L, origin.toString(), "main", "techdebt/run-1-sca-test");

        assertThat(Files.isRegularFile(ws.path().resolve("README.md"))).isTrue();

        Files.writeString(ws.path().resolve("README.md"), "hello\nworld\n");
        assertThat(mgr.hasUncommittedChanges(ws)).isTrue();

        String sha = mgr.commit(ws, "techdebt: update README");
        assertThat(sha).hasSize(40);
        assertThat(mgr.hasUncommittedChanges(ws)).isFalse();

        mgr.cleanup(ws);
        assertThat(Files.exists(ws.path())).isFalse();
    }
}
