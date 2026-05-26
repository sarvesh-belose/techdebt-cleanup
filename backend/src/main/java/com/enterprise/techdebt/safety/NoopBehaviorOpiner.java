package com.enterprise.techdebt.safety;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "techdebt.safety.behavior-opiner", havingValue = "noop", matchIfMissing = true)
public class NoopBehaviorOpiner implements BehaviorOpiner {

    @Override
    public String name() {
        return "noop";
    }

    @Override
    public OpinionResult opine(List<FileChange> changes) {
        return new OpinionResult(0, List.of());
    }
}
