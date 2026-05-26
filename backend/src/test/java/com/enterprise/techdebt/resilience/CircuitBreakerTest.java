package com.enterprise.techdebt.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    @Test
    void successKeepsCircuitClosed() {
        CircuitBreaker cb = new CircuitBreaker("t", 3, Duration.ofSeconds(1));
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            cb.call(() -> { calls.incrementAndGet(); return "ok"; });
        }
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(calls.get()).isEqualTo(10);
    }

    @Test
    void opensAfterConsecutiveFailures() {
        CircuitBreaker cb = new CircuitBreaker("t", 3, Duration.ofMinutes(1));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cb.call(() -> { throw new RuntimeException("boom"); }))
                    .isInstanceOf(RuntimeException.class);
        }
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
        // Subsequent calls fail fast without invoking the supplier
        AtomicInteger invocations = new AtomicInteger();
        assertThatThrownBy(() -> cb.call(() -> {
            invocations.incrementAndGet();
            return "would-have-run";
        })).isInstanceOf(CircuitBreaker.CircuitOpenException.class);
        assertThat(invocations.get()).isZero();
    }

    @Test
    void successResetsTheConsecutiveCount() {
        CircuitBreaker cb = new CircuitBreaker("t", 3, Duration.ofMinutes(1));
        assertThatThrownBy(() -> cb.call(() -> { throw new RuntimeException(); })).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> cb.call(() -> { throw new RuntimeException(); })).isInstanceOf(RuntimeException.class);
        cb.call(() -> "recovery"); // success resets
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.consecutiveFailures()).isZero();
    }

    @Test
    void halfOpenSuccessClosesCircuit() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("t", 2, Duration.ofMillis(20));
        // Trip it
        assertThatThrownBy(() -> cb.call(() -> { throw new RuntimeException(); })).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> cb.call(() -> { throw new RuntimeException(); })).isInstanceOf(RuntimeException.class);
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
        // Wait past reset
        Thread.sleep(30);
        // Next call transitions to HALF_OPEN, succeeds → CLOSED
        String result = cb.call(() -> "ok-after-cooldown");
        assertThat(result).isEqualTo("ok-after-cooldown");
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void invalidConstructionRejected() {
        assertThatThrownBy(() -> new CircuitBreaker("t", 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreaker("t", 3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
