package com.enterprise.techdebt.safety;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores changes by where in the codebase they live. High-risk paths
 * (service, domain, controller, security) score higher than low-risk paths
 * (resources, test, pom). A pom-only fix scores 0; an aggregator of pure
 * test changes scores 0.
 *
 * Score is capped at 40 — file classification alone shouldn't push a diff
 * over the default 60 threshold; the combination with AST and test-delta
 * signals does.
 */
@Component
public class FileClassificationSignal implements SafetySignal {

    private static final int CAP = 40;

    @Override
    public String name() {
        return "file-classification";
    }

    @Override
    public SignalResult evaluate(List<FileChange> changes) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        for (FileChange c : changes) {
            int w = weight(c.getPath());
            if (w > 0) {
                score += w;
                reasons.add(c.getPath() + " (weight " + w + ")");
            }
        }
        score = Math.min(score, CAP);
        return SignalResult.builder()
                .signalName(name())
                .score(score)
                .reasons(reasons)
                .build();
    }

    /** Heuristic weight per file path. Conservative defaults; tunable in Sprint 8. */
    static int weight(String path) {
        if (path == null) return 0;
        String p = path.toLowerCase();
        if (p.endsWith("pom.xml")) return 0;                      // SCA fixes — deterministic, separately validated
        if (p.contains("/src/test/")) return 0;                   // test-only changes handled by test-delta signal
        if (!p.contains("/src/main/")) return 5;                  // resources, config, etc.

        // Security-critical packages first.
        if (containsSegment(p, "security")) return 30;
        if (containsSegment(p, "auth")) return 30;
        if (containsSegment(p, "controller")) return 20;
        if (containsSegment(p, "service")) return 25;
        if (containsSegment(p, "domain")) return 25;
        if (containsSegment(p, "model")) return 20;
        if (containsSegment(p, "repository") || containsSegment(p, "repo")) return 20;
        if (containsSegment(p, "dao")) return 20;
        if (containsSegment(p, "config")) return 15;
        if (containsSegment(p, "api") || containsSegment(p, "web")) return 15;
        if (p.endsWith(".java")) return 10;
        return 5;
    }

    private static boolean containsSegment(String path, String segment) {
        return path.contains("/" + segment + "/") || path.endsWith("/" + segment);
    }
}
