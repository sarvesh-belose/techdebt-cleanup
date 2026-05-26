package com.enterprise.techdebt.build;

import com.enterprise.techdebt.domain.BuildOutcome;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BuildResult {
    BuildOutcome outcome;
    int exitCode;
    long durationMs;
    /** Last ~200 lines of the Maven log, used as refix context if the build failed. */
    String logExcerpt;
    /** Short human-readable summary, surfaced in the run detail UI. */
    String failureSummary;
}
