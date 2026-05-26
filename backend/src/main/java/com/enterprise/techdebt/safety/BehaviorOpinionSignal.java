package com.enterprise.techdebt.safety;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bridges {@link BehaviorOpiner} into the {@link SafetySignal} pipeline so the LLM
 * second-opinion score participates in the total. Capped intentionally low — this is
 * a corroborating signal, never the sole gate.
 */
@Component
public class BehaviorOpinionSignal implements SafetySignal {

    private static final int CAP = 30;

    private final BehaviorOpiner opiner;

    public BehaviorOpinionSignal(BehaviorOpiner opiner) {
        this.opiner = opiner;
    }

    @Override
    public String name() {
        return "behavior-opinion(" + opiner.name() + ")";
    }

    @Override
    public SignalResult evaluate(List<FileChange> changes) {
        BehaviorOpiner.OpinionResult o = opiner.opine(changes);
        return SignalResult.builder()
                .signalName(name())
                .score(Math.min(o.score(), CAP))
                .reasons(o.reasons() == null ? List.of() : o.reasons())
                .build();
    }
}
