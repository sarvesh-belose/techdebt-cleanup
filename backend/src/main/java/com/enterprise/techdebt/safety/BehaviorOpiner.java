package com.enterprise.techdebt.safety;

import java.util.List;

/**
 * Second-opinion query: "does this diff change observable behavior?" Returns a score
 * 0-30 with reasons. Run after the deterministic AST signals — never the sole gate.
 * Implementations:
 *   - {@code noop} (default): always 0, used in dev and tests
 *   - LLM-backed (future): calls the same Copilot path used by the FixGenerator with a
 *     dedicated review prompt; expensive enough to be optional
 */
public interface BehaviorOpiner {
    String name();

    OpinionResult opine(List<FileChange> changes);

    record OpinionResult(int score, List<String> reasons) {
    }
}
