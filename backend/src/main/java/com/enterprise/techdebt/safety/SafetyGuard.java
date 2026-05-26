package com.enterprise.techdebt.safety;

import com.enterprise.techdebt.metrics.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(SafetyGuard.class);

    private final List<SafetySignal> signals;
    private final PlatformMetrics metrics;

    public SafetyGuard(List<SafetySignal> signals, PlatformMetrics metrics) {
        this.signals = signals;
        this.metrics = metrics;
    }

    /**
     * Run all signals over the supplied changes and return an aggregated assessment.
     * Total score is sum of signal scores, capped at 100. If total ≥ threshold,
     * the run is marked blocked and the orchestrator must NOT commit.
     */
    public SafetyAssessment assess(List<FileChange> changes, int threshold) {
        List<SignalResult> results = new ArrayList<>(signals.size());
        int total = 0;
        for (SafetySignal s : signals) {
            try {
                SignalResult r = s.evaluate(changes);
                results.add(r);
                total += r.getScore();
            } catch (RuntimeException ex) {
                log.warn("Safety signal {} failed: {}", s.name(), ex.getMessage());
                results.add(SignalResult.builder()
                        .signalName(s.name())
                        .score(0)
                        .reasons(List.of("signal error: " + ex.getMessage()))
                        .build());
            }
        }
        total = Math.min(total, 100);
        boolean blocked = total >= threshold;
        SafetyAssessment a = SafetyAssessment.builder()
                .totalScore(total)
                .threshold(threshold)
                .blocked(blocked)
                .signals(results)
                .build();
        // Note: SAFETY_BLOCKED gets a second tick in FixRunService.transition when the
        // run actually moves to that state. Recording the assessment outcome here lets
        // ops dashboards see passes too, which the state counter alone wouldn't.
        if (!blocked) {
            metrics.recordSafetyDecision(false);
        }
        log.info("Safety assessment: {}", a.summary());
        return a;
    }
}
