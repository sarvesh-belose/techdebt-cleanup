package com.enterprise.techdebt.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Lifecycle for a per-FixRun workspace: clone the repo into an isolated directory, create
 * a fix branch, expose helpers for commit/diff, and clean up at end of run.
 *
 * Branch naming: {@code techdebt/{runId}-{source}-{shortDesc}}.
 */
@Component
public class RepoWorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(RepoWorkspaceManager.class);

    private final GitRunner git;
    private final Path rootDir;
    private final String committerName;
    private final String committerEmail;

    public RepoWorkspaceManager(GitRunner git,
                                @Value("${techdebt.workspace.root:${java.io.tmpdir}/techdebt-workspaces}") String rootDir,
                                @Value("${techdebt.workspace.committer-name:TechDebt Bot}") String committerName,
                                @Value("${techdebt.workspace.committer-email:techdebt-bot@local}") String committerEmail) {
        this.git = git;
        this.rootDir = Path.of(rootDir);
        this.committerName = committerName;
        this.committerEmail = committerEmail;
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create workspace root " + this.rootDir, e);
        }
    }

    public Workspace cloneRepo(long runId, String repoUrl, String baseBranch, String fixBranch) {
        Path workspacePath = rootDir.resolve("run-" + runId);
        if (Files.exists(workspacePath)) {
            cleanup(workspacePath);
        }
        try {
            Files.createDirectories(workspacePath);
        } catch (IOException e) {
            throw new GitException("Cannot create workspace " + workspacePath, e);
        }

        // Shallow clone for speed; we don't need full history for a fix branch.
        git.run(rootDir, "clone", "--depth", "1", "--branch", baseBranch, repoUrl, workspacePath.toString());

        // Configure local committer identity so commits in this workspace are attributed
        // to the platform service account, not whatever ambient git identity exists.
        git.run(workspacePath, "config", "user.name", committerName);
        git.run(workspacePath, "config", "user.email", committerEmail);

        // Create the fix branch.
        git.run(workspacePath, "checkout", "-b", fixBranch);

        log.info("Workspace ready run={} path={} fixBranch={}", runId, workspacePath, fixBranch);
        return new Workspace(runId, workspacePath, repoUrl, baseBranch, fixBranch);
    }

    public String diff(Workspace ws) {
        return git.run(ws.path(), "diff", "HEAD");
    }

    public String diffSinceBase(Workspace ws) {
        return git.run(ws.path(), "diff", ws.baseBranch() + "...HEAD");
    }

    public String commit(Workspace ws, String message) {
        git.run(ws.path(), "add", "-A");
        git.run(ws.path(), "commit", "-m", message);
        return git.run(ws.path(), "rev-parse", "HEAD").trim();
    }

    public boolean hasUncommittedChanges(Workspace ws) {
        String out = git.run(ws.path(), "status", "--porcelain");
        return out != null && !out.isBlank();
    }

    /**
     * Discard any commits on the fix branch and reset the working tree to {@code baseBranch}.
     * Used by the refix loop after a failed build so the next attempt starts clean.
     */
    public void resetToBase(Workspace ws) {
        git.run(ws.path(), "reset", "--hard", "origin/" + ws.baseBranch());
        git.run(ws.path(), "clean", "-fd");
    }

    /** Files modified vs HEAD (paths relative to the repo root, forward slashes). */
    public java.util.List<String> changedFiles(Workspace ws) {
        String out = git.run(ws.path(), "diff", "--name-only", "HEAD");
        if (out == null || out.isBlank()) return java.util.List.of();
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            String p = line.trim();
            if (!p.isEmpty()) result.add(p);
        }
        return result;
    }

    /** File contents at HEAD, or null if the path doesn't exist in HEAD (newly added file). */
    public String contentAtHead(Workspace ws, String relativePath) {
        try {
            return git.run(ws.path(), "show", "HEAD:" + relativePath);
        } catch (GitException e) {
            return null;
        }
    }

    /** Current working-tree content, or null if the file no longer exists (deleted). */
    public String contentInWorkingTree(Workspace ws, String relativePath) {
        Path p = ws.path().resolve(relativePath);
        if (!Files.isRegularFile(p)) return null;
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return null;
        }
    }

    public void cleanup(Workspace ws) {
        cleanup(ws.path());
    }

    private void cleanup(Path path) {
        if (!Files.exists(path)) return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    // On Windows, files inside .git (notably packed-refs and pack-*.idx)
                    // are marked read-only and Files.delete fails with AccessDeniedException.
                    // Flip them writable before deletion. No-op on POSIX where they're already
                    // owner-writable.
                    try {
                        p.toFile().setWritable(true);
                    } catch (SecurityException ignored) {
                    }
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException e) {
            log.warn("Workspace cleanup failed for {}: {}", path, e.getMessage());
        }
    }

    public static String branchNameFor(long runId, String sourceShortName, String shortDesc) {
        String slug = (shortDesc == null ? "fix" : shortDesc)
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 30) slug = slug.substring(0, 30);
        if (slug.isBlank()) slug = "fix";
        return "techdebt/run-" + runId + "-" + sourceShortName.toLowerCase() + "-" + slug;
    }
}
