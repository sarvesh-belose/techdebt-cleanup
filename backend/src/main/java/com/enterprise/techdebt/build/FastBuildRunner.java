package com.enterprise.techdebt.build;

import com.enterprise.techdebt.workspace.Workspace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fast inner-loop build. Runs {@code mvn -o clean install -DskipITs} by default —
 * offline + skip integration tests — to give the orchestrator a sub-minute signal
 * before triggering the canonical Azure Pipeline.
 *
 * Configurable via:
 *   techdebt.build.fast-tier.mvn-args   (default: "-o clean install -DskipITs")
 *   techdebt.build.fast-tier.timeout-seconds (default: 600)
 */
@Service
public class FastBuildRunner {

    private final BuildExecutor executor;
    private final String[] mvnArgs;
    private final int timeoutSeconds;

    public FastBuildRunner(BuildExecutor executor,
                           @Value("${techdebt.build.fast-tier.mvn-args:-o clean install -DskipITs}") String mvnArgs,
                           @Value("${techdebt.build.fast-tier.timeout-seconds:600}") int timeoutSeconds) {
        this.executor = executor;
        this.mvnArgs = mvnArgs.trim().split("\\s+");
        this.timeoutSeconds = timeoutSeconds;
    }

    public BuildResult run(Workspace workspace) {
        return executor.execute(workspace.path(), mvnArgs, timeoutSeconds);
    }
}
