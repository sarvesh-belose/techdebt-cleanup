package com.enterprise.techdebt.resilience;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Minimal in-process circuit breaker. Three states (CLOSED → OPEN → HALF_OPEN) per the
 * standard pattern:
 *  - CLOSED: requests flow through. {@code N} consecutive failures opens the breaker.
 *  - OPEN: requests fail fast with {@link CircuitOpenException}. After
 *    {@code resetTimeout}, the next request transitions to HALF_OPEN.
 *  - HALF_OPEN: a single request is allowed through to probe. Success → CLOSED,
 *    failure → OPEN with a fresh cooldown.
 *
 * Deliberately simple: no rolling-window stats, no sliding percentages, no separate
 * configuration for slow calls. The point is to fail fast when an upstream is clearly
 * dead, not to do sophisticated load shedding. Replace with Resilience4j if those
 * features become necessary.
 */
public final class CircuitBreaker {

    private final String name;
    private final int failureThreshold;
    private final Duration resetTimeout;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openedAt = new AtomicReference<>(Instant.EPOCH);

    public CircuitBreaker(String name, int failureThreshold, Duration resetTimeout) {
        if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold must be >= 1");
        if (resetTimeout == null || resetTimeout.isNegative() || resetTimeout.isZero()) {
            throw new IllegalArgumentException("resetTimeout must be positive");
        }
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeout = resetTimeout;
    }

    public <T> T call(Supplier<T> supplier) {
        State current = state.get();
        if (current == State.OPEN) {
            if (Instant.now().isAfter(openedAt.get().plus(resetTimeout))) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
            } else {
                throw new CircuitOpenException(name, "circuit '" + name + "' is OPEN, retry after "
                        + openedAt.get().plus(resetTimeout));
            }
        }
        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (RuntimeException ex) {
            onFailure();
            throw ex;
        }
    }

    public State state() {
        return state.get();
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    private void onFailure() {
        int n = consecutiveFailures.incrementAndGet();
        if (n >= failureThreshold) {
            state.set(State.OPEN);
            openedAt.set(Instant.now());
        }
    }

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public static final class CircuitOpenException extends RuntimeException {
        public CircuitOpenException(String circuit, String message) {
            super(message);
        }
    }
}
