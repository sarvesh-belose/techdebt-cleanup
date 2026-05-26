package com.enterprise.techdebt.safety;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SafetyAssessment {
    int totalScore;
    int threshold;
    boolean blocked;
    List<SignalResult> signals;

    public String summary() {
        StringBuilder b = new StringBuilder();
        b.append("score=").append(totalScore).append("/").append(threshold);
        if (blocked) b.append(" BLOCKED");
        for (SignalResult s : signals) {
            if (s.getScore() == 0) continue;
            b.append("; ").append(s.getSignalName()).append("=").append(s.getScore());
            if (!s.getReasons().isEmpty()) {
                b.append("(");
                for (int i = 0; i < s.getReasons().size(); i++) {
                    if (i > 0) b.append("; ");
                    b.append(s.getReasons().get(i));
                }
                b.append(")");
            }
        }
        return b.toString();
    }
}
