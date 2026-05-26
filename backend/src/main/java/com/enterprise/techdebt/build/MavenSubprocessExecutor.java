package com.enterprise.techdebt.build;

import com.enterprise.techdebt.domain.BuildOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs {@code mvn} in a workspace as a subprocess. Captures the full log to memory
 * (build runners are short-lived and bounded by timeout), then trims to the last N
 * lines for storage and refix context.
 *
 * In Sprint 6, this is replaced by a Kubernetes-Job-based executor that mounts a shared
 * {@code .m2} cache volume; the interface stays the same.
 */
@Component
public class MavenSubprocessExecutor implements BuildExecutor {

    private static final Logger log = LoggerFactory.getLogger(MavenSubprocessExecutor.class);
    private static final int LOG_TAIL_LINES = 200;

    private static final Pattern FAILURE_MARKER = Pattern.compile(
            "(?m)^\\[ERROR\\].*");
    private static final Pattern TEST_FAILURE = Pattern.compile(
            "(?m)^\\[ERROR\\]\\s+(Failures|Errors):\\s*$|(?m)^\\[ERROR\\]\\s+\\S+Test.*<<<\\s+(FAILURE|ERROR)");
    private static final Pattern COMPILE_FAILURE = Pattern.compile(
            "(?m)^\\[ERROR\\].*COMPILATION ERROR|symbol:\\s+|cannot find symbol");

    private final String mvnPath;

    public MavenSubprocessExecutor(@Value("${techdebt.build.mvn-path:mvn}") String mvnPath) {
        this.mvnPath = mvnPath;
    }

    @Override
    public BuildResult execute(Path workspacePath, String[] mavenArgs, int timeoutSeconds) {
        long started = System.currentTimeMillis();
        List<String> cmd = new ArrayList<>(mavenArgs.length + 1);
        cmd.add(mvnPath);
        for (String arg : mavenArgs) cmd.add(arg);

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(workspacePath.toFile())
                .redirectErrorStream(true);
        // Maven respects MAVEN_OPTS for JVM tuning; we don't pin it so users can override.
        // Disable colorized output so log parsing stays deterministic.
        pb.environment().put("MAVEN_BATCH_ECHO", "false");

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return BuildResult.builder()
                    .outcome(BuildOutcome.INFRA_ERROR)
                    .exitCode(-1)
                    .durationMs(System.currentTimeMillis() - started)
                    .logExcerpt(null)
                    .failureSummary("Failed to start mvn: " + e.getMessage())
                    .build();
        }

        StringBuilder out = new StringBuilder(8192);
        try {
            byte[] bytes = process.getInputStream().readAllBytes();
            out.append(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("I/O error reading mvn output: {}", e.getMessage());
        }

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return BuildResult.builder()
                    .outcome(BuildOutcome.INFRA_ERROR)
                    .exitCode(-1)
                    .durationMs(System.currentTimeMillis() - started)
                    .logExcerpt(tailLines(out.toString(), LOG_TAIL_LINES))
                    .failureSummary("mvn interrupted")
                    .build();
        }
        if (!finished) {
            process.destroyForcibly();
            return BuildResult.builder()
                    .outcome(BuildOutcome.TIMEOUT)
                    .exitCode(-1)
                    .durationMs(System.currentTimeMillis() - started)
                    .logExcerpt(tailLines(out.toString(), LOG_TAIL_LINES))
                    .failureSummary("mvn timed out after " + timeoutSeconds + "s")
                    .build();
        }

        int exit = process.exitValue();
        long duration = System.currentTimeMillis() - started;
        BuildOutcome outcome = classify(exit, out.toString());
        String summary = exit == 0 ? "BUILD SUCCESS" : firstFailureSummary(out.toString());
        return BuildResult.builder()
                .outcome(outcome)
                .exitCode(exit)
                .durationMs(duration)
                .logExcerpt(tailLines(out.toString(), LOG_TAIL_LINES))
                .failureSummary(summary)
                .build();
    }

    static BuildOutcome classify(int exit, String log) {
        if (exit == 0) return BuildOutcome.SUCCESS;
        if (TEST_FAILURE.matcher(log).find()) return BuildOutcome.TEST_FAILURE;
        if (COMPILE_FAILURE.matcher(log).find()) return BuildOutcome.COMPILE_FAILURE;
        return BuildOutcome.COMPILE_FAILURE;   // default for non-zero exits with no clear marker
    }

    static String firstFailureSummary(String log) {
        Matcher m = FAILURE_MARKER.matcher(log);
        if (m.find()) {
            String line = m.group();
            return line.length() > 400 ? line.substring(0, 400) : line;
        }
        return "BUILD FAILURE";
    }

    static String tailLines(String text, int n) {
        if (text == null) return null;
        java.util.List<String> lines = text.lines().toList();
        if (lines.size() <= n) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = lines.size() - n; i < lines.size(); i++) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }
}
