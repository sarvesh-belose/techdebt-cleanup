package com.enterprise.techdebt.build;

import java.nio.file.Path;

/**
 * Runs a build in a workspace and returns the result. Implementations:
 *   - {@link MavenSubprocessExecutor} for local/dev (shells out to {@code mvn})
 *   - Kubernetes runner-pod executor lands in Sprint 6 (J1)
 */
public interface BuildExecutor {
    BuildResult execute(Path workspacePath, String[] mavenArgs, int timeoutSeconds);
}
