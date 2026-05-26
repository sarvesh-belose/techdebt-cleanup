package com.enterprise.techdebt.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDeltaSignalTest {

    private final TestDeltaSignal signal = new TestDeltaSignal();

    @Test
    void mainOnlyChangeScoresZero() {
        SignalResult r = signal.evaluate(List.of(
                new FileChange("svc/src/main/java/a/Foo.java", "a", "b")
        ));
        assertThat(r.getScore()).isZero();
    }

    @Test
    void testOnlyChangeScoresZero() {
        SignalResult r = signal.evaluate(List.of(
                new FileChange("svc/src/test/java/a/UnrelatedTest.java", "a", "b")
        ));
        assertThat(r.getScore()).isZero();
    }

    @Test
    void modifyingTestAndUnrelatedMainScoresLow() {
        SignalResult r = signal.evaluate(List.of(
                new FileChange("svc/src/main/java/a/Foo.java", "a", "b"),
                new FileChange("svc/src/test/java/a/BarTest.java", "a", "b")
        ));
        assertThat(r.getScore()).isEqualTo(15);
        assertThat(r.getReasons()).anyMatch(s -> s.contains("both src/main and src/test"));
    }

    @Test
    void matchingTestAndMainClassScoresHigh() {
        SignalResult r = signal.evaluate(List.of(
                new FileChange("svc/src/main/java/a/PaymentService.java", "a", "b"),
                new FileChange("svc/src/test/java/a/PaymentServiceTest.java", "a", "b")
        ));
        assertThat(r.getScore()).isEqualTo(40); // 15 (mixed) + 25 (matched)
        assertThat(r.getReasons()).anyMatch(s -> s.contains("PaymentServiceTest"));
    }
}
