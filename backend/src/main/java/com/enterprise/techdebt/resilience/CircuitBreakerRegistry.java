package com.enterprise.techdebt.resilience;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * One {@link CircuitBreaker} per logical upstream. Today we name them by source
 * (e.g. "veracode-sca", "veracode-sast", "sonarqube"). Defaults are conservative —
 * five failures opens the circuit for two minutes. Configurable via properties.
 */
@Component
public class CircuitBreakerRegistry {

    private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final int defaultThreshold;
    private final Duration defaultReset;

    public CircuitBreakerRegistry(
            @Value("${techdebt.resilience.failure-threshold:5}") int defaultThreshold,
            @Value("${techdebt.resilience.reset-seconds:120}") int resetSeconds) {
        this.defaultThreshold = defaultThreshold;
        this.defaultReset = Duration.ofSeconds(resetSeconds);
    }

    public CircuitBreaker forName(String name) {
        return breakers.computeIfAbsent(name,
                n -> new CircuitBreaker(n, defaultThreshold, defaultReset));
    }
}
