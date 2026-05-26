package com.enterprise.techdebt.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileClassificationSignalTest {

    private final FileClassificationSignal signal = new FileClassificationSignal();

    @Test
    void pomOnlyChangeScoresZero() {
        SignalResult r = signal.evaluate(List.of(
                new FileChange("pom.xml", "<old/>", "<new/>")
        ));
        assertThat(r.getScore()).isZero();
    }

    @Test
    void testOnlyChangeScoresZero() {
        SignalResult r = signal.evaluate(List.of(
                new FileChange("payments/src/test/java/FooTest.java", "before", "after")
        ));
        assertThat(r.getScore()).isZero();
    }

    @Test
    void serviceChangeScoresHigher() {
        SignalResult r = signal.evaluate(List.of(
                new FileChange("payments/src/main/java/com/acme/service/PaymentService.java", "before", "after")
        ));
        assertThat(r.getScore()).isEqualTo(25);
        assertThat(r.getReasons()).anyMatch(s -> s.contains("PaymentService"));
    }

    @Test
    void securityPathScoresHighest() {
        SignalResult r = signal.evaluate(List.of(
                new FileChange("payments/src/main/java/com/acme/security/AuthFilter.java", "before", "after")
        ));
        assertThat(r.getScore()).isEqualTo(30);
    }

    @Test
    void capsAt40() {
        SignalResult r = signal.evaluate(List.of(
                new FileChange("a/src/main/java/com/acme/security/A.java", "", ""),
                new FileChange("a/src/main/java/com/acme/service/B.java", "", ""),
                new FileChange("a/src/main/java/com/acme/domain/C.java", "", ""),
                new FileChange("a/src/main/java/com/acme/controller/D.java", "", "")
        ));
        assertThat(r.getScore()).isEqualTo(40);
    }
}
