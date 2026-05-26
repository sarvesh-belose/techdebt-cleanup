package com.enterprise.techdebt.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the {@code git} CLI. Kept as a separate class so tests can mock
 * the subprocess layer without dragging the full WorkspaceManager flow.
 */
@Component
public class GitRunner {

    private static final Logger log = LoggerFactory.getLogger(GitRunner.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    /**
     * Run a git command and return stdout. Throws {@link GitException} on non-zero exit.
     */
    public String run(Path workingDir, String... args) {
        return run(workingDir, DEFAULT_TIMEOUT_SECONDS, args);
    }

    public String run(Path workingDir, int timeoutSeconds, String... args) {
        List<String> cmd = new ArrayList<>(args.length + 1);
        cmd.add("git");
        for (String a : args) cmd.add(a);

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
        if (workingDir != null) pb.directory(workingDir.toFile());
        // Deterministic git behavior — no pager, no interactive prompts.
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("GIT_PAGER", "cat");

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new GitException("Failed to start git " + String.join(" ", args), e);
        }
        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitException("git " + args[0] + " timed out after " + timeoutSeconds + "s", -1, null);
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.exitValue();
            if (code != 0) {
                throw new GitException("git " + String.join(" ", args) + " failed", code, stderr);
            }
            log.debug("git {} ok in {}", args[0], workingDir);
            return stdout;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new GitException("git " + args[0] + " interrupted", e);
        } catch (IOException e) {
            throw new GitException("git " + args[0] + " io error", e);
        }
    }
}
