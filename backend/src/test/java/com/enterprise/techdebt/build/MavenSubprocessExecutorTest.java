package com.enterprise.techdebt.build;

import com.enterprise.techdebt.domain.BuildOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MavenSubprocessExecutorTest {

    @Test
    void classifyMapsSuccessToSuccess() {
        assertThat(MavenSubprocessExecutor.classify(0, "BUILD SUCCESS"))
                .isEqualTo(BuildOutcome.SUCCESS);
    }

    @Test
    void classifyDetectsTestFailure() {
        String log = """
                [INFO] Running com.acme.PaymentServiceTest
                [ERROR] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0
                [ERROR] PaymentServiceTest.transfer:42 expected 100 but was 99
                [ERROR] Failures:
                [ERROR]   PaymentServiceTest.transfer:42
                """;
        assertThat(MavenSubprocessExecutor.classify(1, log))
                .isEqualTo(BuildOutcome.TEST_FAILURE);
    }

    @Test
    void classifyDetectsCompileFailure() {
        String log = """
                [INFO] BUILD FAILURE
                [ERROR] COMPILATION ERROR :
                [ERROR] /src/main/java/Foo.java:[12,8] cannot find symbol
                """;
        assertThat(MavenSubprocessExecutor.classify(1, log))
                .isEqualTo(BuildOutcome.COMPILE_FAILURE);
    }

    @Test
    void tailLinesPreservesShortLogsAndTrimsLong() {
        String shortLog = "line1\nline2\n";
        assertThat(MavenSubprocessExecutor.tailLines(shortLog, 10)).isEqualTo(shortLog);

        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 500; i++) big.append("line ").append(i).append('\n');
        String tail = MavenSubprocessExecutor.tailLines(big.toString(), 50);
        assertThat(tail).startsWith("line 450\n");
        assertThat(tail.split("\n").length).isEqualTo(50);
    }

    @Test
    void firstFailureSummaryReturnsFirstErrorLine() {
        String log = """
                [INFO] starting
                [ERROR] something went wrong in module foo
                [ERROR] more detail follows
                """;
        assertThat(MavenSubprocessExecutor.firstFailureSummary(log))
                .contains("something went wrong in module foo");
    }
}
