package com.enterprise.techdebt.copilot;

import com.enterprise.techdebt.workspace.GitException;
import com.enterprise.techdebt.workspace.GitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Production fix generator: runs the GitHub Copilot CLI as a subprocess in the
 * cloned workspace, then captures the unified diff via {@code git diff HEAD}.
 *
 * Activated when {@code techdebt.copilot.generator=copilot}. The {@link NoopFixGenerator}
 * is the default until the deployment has Copilot CLI provisioned.
 */
@Component
@ConditionalOnProperty(name = "techdebt.copilot.generator", havingValue = "copilot")
public class CopilotDriver implements FixGenerator {

    private static final Logger log = LoggerFactory.getLogger(CopilotDriver.class);

    private final GitRunner gitRunner;
    private final String cliPath;
    private final int timeoutSeconds;

    public CopilotDriver(GitRunner gitRunner,
                         @Value("${techdebt.copilot.cli-path:gh}") String cliPath,
                         @Value("${techdebt.copilot.timeout-seconds:300}") int timeoutSeconds) {
        this.gitRunner = gitRunner;
        this.cliPath = cliPath;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String name() {
        return "copilot-cli";
    }

    @Override
    public FixResult generate(FixRequest request) {
        String prompt = CopilotPromptBuilder.build(request);
        Path workspace = request.getWorkspacePath();
        Path promptFile = workspace.resolve(".techdebt-prompt.txt");
        try {
            Files.writeString(promptFile, prompt, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CopilotException("Failed to write prompt file: " + e.getMessage(), e);
        }

        ProcessBuilder pb = new ProcessBuilder(cliPath, "copilot", "suggest", "--target", "shell",
                "--prompt-file", promptFile.toString())
                .directory(workspace.toFile())
                .redirectErrorStream(false);
        pb.environment().put("GH_NO_UPDATE_NOTIFIER", "1");

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new CopilotException("Failed to start `" + cliPath + " copilot`: " + e.getMessage(), e);
        }
        StringBuilder stdoutB = new StringBuilder();
        StringBuilder stderrB = new StringBuilder();
        try {
            boolean done = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                throw new CopilotException("Copilot timed out after " + timeoutSeconds + "s");
            }
            stdoutB.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            stderrB.append(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            int exit = process.exitValue();
            if (exit != 0) {
                throw new CopilotException("Copilot exit=" + exit + " stderr=" + truncate(stderrB.toString(), 400));
            }
            log.debug("Copilot stdout ({} bytes) for run={}", stdoutB.length(), request.getRunId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new CopilotException("Copilot interrupted", e);
        } catch (IOException e) {
            throw new CopilotException("Copilot I/O error", e);
        } finally {
            try {
                Files.deleteIfExists(promptFile);
            } catch (IOException ignored) {
            }
        }

        String diff;
        try {
            diff = gitRunner.run(workspace, "diff", "HEAD");
        } catch (GitException ge) {
            throw new CopilotException("git diff failed after Copilot run: " + ge.getMessage(), ge);
        }
        boolean changed = diff != null && !diff.isBlank();
        return FixResult.builder()
                .changed(changed)
                .diff(diff)
                .explanation(truncate(stdoutB.toString(), 4000))
                .generator(name())
                .build();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + " …(truncated)";
    }
}
