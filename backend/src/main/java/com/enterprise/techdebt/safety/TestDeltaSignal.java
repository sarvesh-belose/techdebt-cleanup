package com.enterprise.techdebt.safety;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Flags diffs where the generator also modified tests for changed production classes.
 * Rationale: if a fix changed behavior, the LLM often "fixes" the test to keep it green —
 * which masks the behavior change. Independent rewrites of tests for changed methods
 * are a strong behavior-change signal.
 *
 * Score is added when:
 *  - A test file under src/test/ was modified alongside any src/main/ change           +15
 *  - A modified test file's class name maps to a modified production class             +25
 *    (e.g. PaymentServiceTest.java changed AND PaymentService.java changed)
 */
@Component
public class TestDeltaSignal implements SafetySignal {

    private static final int CAP = 40;

    @Override
    public String name() {
        return "test-delta";
    }

    @Override
    public SignalResult evaluate(List<FileChange> changes) {
        Set<String> mainClassesChanged = new HashSet<>();
        Set<String> testClassesChanged = new HashSet<>();
        boolean anyMainChange = false;
        boolean anyTestChange = false;

        for (FileChange c : changes) {
            if (!c.isJava()) continue;
            String simple = simpleClassName(c.getPath());
            if (simple == null) continue;
            if (c.isMain()) {
                anyMainChange = true;
                mainClassesChanged.add(simple);
            } else if (c.isTest()) {
                anyTestChange = true;
                testClassesChanged.add(simple);
            }
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (anyMainChange && anyTestChange) {
            score += 15;
            reasons.add("diff modifies both src/main and src/test in the same run");
        }
        // For each test class, see if it corresponds to a changed main class.
        // Conventions: PaymentServiceTest, PaymentServiceTests, PaymentServiceIT.
        for (String testClass : testClassesChanged) {
            for (String suffix : List.of("Test", "Tests", "IT")) {
                if (testClass.endsWith(suffix)) {
                    String candidate = testClass.substring(0, testClass.length() - suffix.length());
                    if (mainClassesChanged.contains(candidate)) {
                        score += 25;
                        reasons.add("test class " + testClass + " changed alongside " + candidate);
                        break;
                    }
                }
            }
        }
        return SignalResult.builder()
                .signalName(name())
                .score(Math.min(score, CAP))
                .reasons(reasons)
                .build();
    }

    private String simpleClassName(String path) {
        if (path == null || !path.endsWith(".java")) return null;
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String file = path.substring(slash + 1);
        return file.substring(0, file.length() - ".java".length());
    }
}
