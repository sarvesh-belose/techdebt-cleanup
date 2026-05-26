package com.enterprise.techdebt.copilot;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FixResult {
    boolean changed;
    /** Unified diff of the changes the generator made to the workspace. */
    String diff;
    /** Human-readable explanation surfaced to reviewers. */
    String explanation;
    /** Identifier of the generator that produced the fix (e.g. "copilot-cli", "noop"). */
    String generator;
}
