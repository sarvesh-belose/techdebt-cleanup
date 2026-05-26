package com.enterprise.techdebt.copilot;

/**
 * Pluggable fix-generation backend. Default production impl shells out to the
 * GitHub Copilot CLI; tests and local-dev use the no-op stub.
 *
 * The contract:
 *  - Implementations read the workspace at {@link FixRequest#getWorkspacePath()},
 *    make edits there, and return a {@link FixResult} containing the diff.
 *  - Implementations MUST NOT commit — committing is the orchestrator's job so the
 *    safety guard can inspect the unstaged diff first.
 *  - Implementations MUST honor the "preserve business logic" intent. The prompt
 *    template enforces this for LLM-backed implementations.
 */
public interface FixGenerator {
    String name();

    FixResult generate(FixRequest request);
}
