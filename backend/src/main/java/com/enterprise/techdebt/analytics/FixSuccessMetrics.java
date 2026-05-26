package com.enterprise.techdebt.analytics;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class FixSuccessMetrics {
    Instant since;
    long totalRuns;
    long mergedRuns;
    long failedRuns;
    long safetyBlockedRuns;
    long prOpenedRuns;
    double successRate;
    double safetyBlockRate;
    Double averageRefixAttempts;
    /** Counts of issues created per source within the window. */
    Map<String, Long> issuesIngestedBySource;
    /** Per-source counts of issues currently OPEN. */
    Map<String, Long> openIssuesBySource;
    /** Per-source 90-day MTTR in seconds. */
    Map<String, Double> mttrSecondsBySource;
    /** Window of state transitions used for the MTTR estimate. */
    List<String> notes;
}
