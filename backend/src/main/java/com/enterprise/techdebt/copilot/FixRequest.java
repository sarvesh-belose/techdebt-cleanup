package com.enterprise.techdebt.copilot;

import com.enterprise.techdebt.domain.Issue;
import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;
import java.util.List;

@Value
@Builder
public class FixRequest {
    long runId;
    Path workspacePath;
    List<Issue> issues;
    /** Optional build error log from a prior attempt — fed back to the LLM for refix. */
    String previousBuildError;
    int attemptNumber;
}
