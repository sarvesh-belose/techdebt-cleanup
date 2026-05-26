package com.enterprise.techdebt.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyGuardTest {

    private final SafetyGuard guard = new SafetyGuard(
            List.of(
                    new FileClassificationSignal(),
                    new AstDiffSignal(),
                    new TestDeltaSignal()),
            new com.enterprise.techdebt.metrics.PlatformMetrics(
                    new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    @Test
    void pomBumpIsNeverBlocked() {
        List<FileChange> changes = List.of(
                new FileChange("pom.xml",
                        "<dependency><version>2.14.0</version></dependency>",
                        "<dependency><version>2.17.1</version></dependency>")
        );
        SafetyAssessment a = guard.assess(changes, 60);
        assertThat(a.isBlocked()).isFalse();
        assertThat(a.getTotalScore()).isZero();
    }

    @Test
    void preAuthorizeRemovalIsBlocked() {
        String before = "package a; import org.springframework.security.access.prepost.PreAuthorize;"
                + " public class TransferService { @PreAuthorize(\"hasRole('ADMIN')\") public void send() {} }";
        String after  = "package a; public class TransferService { public void send() {} }";
        SafetyAssessment a = guard.assess(List.of(
                new FileChange("svc/src/main/java/a/service/TransferService.java", before, after)), 60);
        assertThat(a.isBlocked()).isTrue();
        assertThat(a.getTotalScore()).isGreaterThanOrEqualTo(60);
    }

    @Test
    void thresholdIsRespectedPerProject() {
        // Same diff: at strict threshold 30 it blocks; at lenient 90 it doesn't.
        String before = "package a; public class A { public int x(int a) { return a; } }";
        String after  = "package a; public class A { public long x(long a) { return a; } }";
        FileChange fc = new FileChange("svc/src/main/java/a/service/A.java", before, after);

        SafetyAssessment strict = guard.assess(List.of(fc), 30);
        assertThat(strict.isBlocked()).isTrue();

        SafetyAssessment lenient = guard.assess(List.of(fc), 90);
        assertThat(lenient.isBlocked()).isFalse();
    }

    @Test
    void summaryContainsScoreAndBlockedFlag() {
        SafetyAssessment a = SafetyAssessment.builder()
                .totalScore(75)
                .threshold(60)
                .blocked(true)
                .signals(List.of(
                        SignalResult.builder().signalName("ast-diff").score(75)
                                .reasons(List.of("CTl-flow change")).build()))
                .build();
        assertThat(a.summary()).contains("75/60");
        assertThat(a.summary()).contains("BLOCKED");
        assertThat(a.summary()).contains("ast-diff=75");
    }
}
