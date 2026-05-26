package com.enterprise.techdebt.metrics;

import com.enterprise.techdebt.domain.FixRunState;
import com.enterprise.techdebt.domain.IssueSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Custom platform metrics published via Micrometer → /actuator/prometheus.
 *
 * Counters / timers are created lazily and cached per dimension to avoid registering
 * the same meter twice (Micrometer rejects duplicates with subtly different tags).
 */
@Component
public class PlatformMetrics {

    private final MeterRegistry registry;

    private final ConcurrentMap<String, Timer> scanTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> stateCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> safetyCounters = new ConcurrentHashMap<>();
    private final DistributionSummary refixDepth;
    private final Counter scanFailures;

    public PlatformMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.refixDepth = DistributionSummary.builder("techdebt.fix_run.refix_depth")
                .description("Number of attempts per fix run (1 = no refix needed)")
                .baseUnit("attempts")
                .publishPercentileHistogram()
                .register(registry);
        this.scanFailures = Counter.builder("techdebt.scan.failures")
                .description("Scans that ended in connector error")
                .register(registry);
    }

    public void recordScanDuration(IssueSource source, long millis) {
        scanTimers.computeIfAbsent(source.name(), s ->
                Timer.builder("techdebt.scan.duration")
                        .description("Wall-clock time of a single ingestion scan")
                        .tag("source", s)
                        .publishPercentileHistogram()
                        .register(registry))
                .record(java.time.Duration.ofMillis(millis));
    }

    public void recordScanFailure() {
        scanFailures.increment();
    }

    public void recordFixRunState(FixRunState state) {
        stateCounters.computeIfAbsent(state.name(), s ->
                Counter.builder("techdebt.fix_run.state")
                        .description("Counts of fix runs that reached each state")
                        .tag("state", s)
                        .register(registry))
                .increment();
    }

    public void recordSafetyDecision(boolean blocked) {
        String tag = blocked ? "blocked" : "passed";
        safetyCounters.computeIfAbsent(tag, t ->
                Counter.builder("techdebt.safety.decisions")
                        .description("Safety Guard decisions (passed / blocked)")
                        .tag("outcome", t)
                        .register(registry))
                .increment();
    }

    public void recordRefixDepth(int attemptCount) {
        if (attemptCount > 0) {
            refixDepth.record(attemptCount);
        }
    }
}
