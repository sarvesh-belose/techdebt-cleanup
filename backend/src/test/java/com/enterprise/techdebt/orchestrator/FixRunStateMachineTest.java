package com.enterprise.techdebt.orchestrator;

import com.enterprise.techdebt.domain.FixRunState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixRunStateMachineTest {

    @Test
    void happyPathTransitionsAreLegal() {
        assertThat(FixRunStateMachine.canTransition(FixRunState.QUEUED, FixRunState.CLONING)).isTrue();
        assertThat(FixRunStateMachine.canTransition(FixRunState.CLONING, FixRunState.FIXING)).isTrue();
        assertThat(FixRunStateMachine.canTransition(FixRunState.FIXING, FixRunState.FAST_BUILD)).isTrue();
        assertThat(FixRunStateMachine.canTransition(FixRunState.FAST_BUILD, FixRunState.CANONICAL_BUILD)).isTrue();
        assertThat(FixRunStateMachine.canTransition(FixRunState.CANONICAL_BUILD, FixRunState.PR_OPENED)).isTrue();
        assertThat(FixRunStateMachine.canTransition(FixRunState.PR_OPENED, FixRunState.MERGED)).isTrue();
    }

    @Test
    void abortIsLegalFromAnyNonTerminalState() {
        for (FixRunState s : FixRunState.values()) {
            if (FixRunStateMachine.isTerminal(s)) continue;
            assertThat(FixRunStateMachine.canTransition(s, FixRunState.ABORTED))
                    .as("abort from %s", s).isTrue();
        }
    }

    @Test
    void refixLoopIsLegal() {
        assertThat(FixRunStateMachine.canTransition(FixRunState.FAST_BUILD, FixRunState.FIXING)).isTrue();
    }

    @Test
    void safetyOverrideRoutesBackToBuild() {
        assertThat(FixRunStateMachine.canTransition(FixRunState.SAFETY_BLOCKED, FixRunState.FAST_BUILD)).isTrue();
    }

    @Test
    void terminalStatesHaveNoSuccessors() {
        assertThat(FixRunStateMachine.isTerminal(FixRunState.MERGED)).isTrue();
        assertThat(FixRunStateMachine.isTerminal(FixRunState.FAILED)).isTrue();
        assertThat(FixRunStateMachine.isTerminal(FixRunState.ABORTED)).isTrue();
        assertThat(FixRunStateMachine.canTransition(FixRunState.MERGED, FixRunState.PR_OPENED)).isFalse();
    }

    @Test
    void illegalTransitionThrows() {
        assertThatThrownBy(() -> FixRunStateMachine.requireTransition(FixRunState.QUEUED, FixRunState.MERGED))
                .isInstanceOf(FixRunStateMachine.IllegalStateTransition.class);
        assertThatThrownBy(() -> FixRunStateMachine.requireTransition(FixRunState.FIXING, FixRunState.PR_OPENED))
                .isInstanceOf(FixRunStateMachine.IllegalStateTransition.class);
    }
}
