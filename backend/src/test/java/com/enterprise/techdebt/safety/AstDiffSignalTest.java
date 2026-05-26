package com.enterprise.techdebt.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AstDiffSignalTest {

    private final AstDiffSignal signal = new AstDiffSignal();

    @Test
    void whitespaceOnlyChangeScoresZero() {
        String before = "package a;\npublic class C { public int x() { return 1; } }";
        String after  = "package a;\npublic class C {\n    public int x() {\n        return 1;\n    }\n}";
        SignalResult r = signal.evaluate(List.of(
                new FileChange("src/main/java/a/C.java", before, after)));
        assertThat(r.getScore()).isZero();
    }

    @Test
    void varRenameInBodyScoresZero() {
        // Renaming a local doesn't change signature, annotations, conditional count,
        // return count, or SQL — the signal should let it through.
        String before = "package a; public class C { public int x(int a) { int b = a + 1; return b; } }";
        String after  = "package a; public class C { public int x(int a) { int renamed = a + 1; return renamed; } }";
        SignalResult r = signal.evaluate(List.of(
                new FileChange("src/main/java/a/C.java", before, after)));
        assertThat(r.getScore()).isZero();
    }

    @Test
    void flippedConditionalIsFlagged() {
        String before = "package a; public class C { public int x(int a) { if (a > 0) return 1; return 2; } }";
        String after  = "package a; public class C { public int x(int a) { if (a > 0) { return 1; } return 2; if (a < 0) return 3; } }";
        SignalResult r = signal.evaluate(List.of(
                new FileChange("src/main/java/a/C.java", before, after)));
        // Behavior change: extra if + extra return → both signals fire
        assertThat(r.getScore()).isGreaterThan(0);
        assertThat(r.getReasons()).anyMatch(s -> s.contains("control-flow"));
    }

    @Test
    void methodSignatureChangeIsHighSignal() {
        String before = "package a; public class C { public int x(int a) { return a; } }";
        String after  = "package a; public class C { public int x(long a) { return (int) a; } }";
        SignalResult r = signal.evaluate(List.of(
                new FileChange("src/main/java/a/C.java", before, after)));
        // Note: methodKey uses param types, so signature change shows up as
        // "method removed" (old key) which is even more severe — still a clear signal.
        assertThat(r.getScore()).isGreaterThanOrEqualTo(25);
        assertThat(r.getReasons()).anyMatch(s -> s.contains("signature") || s.contains("removed"));
    }

    @Test
    void preAuthorizeRemovalIsCritical() {
        String before = "package a; import org.springframework.security.access.prepost.PreAuthorize;"
                + " public class C { @PreAuthorize(\"hasRole('ADMIN')\") public void delete() {} }";
        String after  = "package a; public class C { public void delete() {} }";
        SignalResult r = signal.evaluate(List.of(
                new FileChange("src/main/java/a/C.java", before, after)));
        assertThat(r.getScore()).isGreaterThanOrEqualTo(35);
        assertThat(r.getReasons()).anyMatch(s -> s.contains("PreAuthorize"));
    }

    @Test
    void sqlLiteralChangeIsFlagged() {
        String before = "package a; public class C { public String q() { return \"select * from accounts where id=?\"; } }";
        String after  = "package a; public class C { public String q() { return \"select * from accounts_v2 where id=?\"; } }";
        SignalResult r = signal.evaluate(List.of(
                new FileChange("src/main/java/a/C.java", before, after)));
        assertThat(r.getScore()).isGreaterThanOrEqualTo(20);
        assertThat(r.getReasons()).anyMatch(s -> s.contains("SQL"));
    }

    @Test
    void testFilesAreIgnored() {
        // AST diff signal explicitly skips src/test/ — TestDeltaSignal handles those.
        String before = "package a; public class FooTest { @org.junit.Test public void t() {} }";
        String after  = "package a; public class FooTest { @org.junit.Test public void t() { /* changed */ } }";
        SignalResult r = signal.evaluate(List.of(
                new FileChange("src/test/java/a/FooTest.java", before, after)));
        assertThat(r.getScore()).isZero();
    }
}
