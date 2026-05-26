package com.enterprise.techdebt.copilot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default fix generator for local dev and CI where the Copilot CLI is unavailable.
 * Returns no-change. The orchestrator interprets {@code changed=false} as
 * "nothing to commit" and transitions the run to FAILED with a clear reason
 * rather than producing an empty PR.
 */
@Component
@ConditionalOnProperty(name = "techdebt.copilot.generator", havingValue = "noop", matchIfMissing = true)
public class NoopFixGenerator implements FixGenerator {

    @Override
    public String name() {
        return "noop";
    }

    @Override
    public FixResult generate(FixRequest request) {
        return FixResult.builder()
                .changed(false)
                .generator(name())
                .explanation("No-op generator is active — set techdebt.copilot.generator=copilot to enable Copilot CLI.")
                .build();
    }
}
