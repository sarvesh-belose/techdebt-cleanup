package com.enterprise.techdebt.orchestrator;

import com.enterprise.techdebt.domain.FixRunState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure state machine: encodes legal transitions, no I/O. The orchestrator consults it
 * before persisting any state change so an invalid transition surfaces as an
 * IllegalStateTransition rather than a silent corruption.
 *
 * Diagram:
 *
 *   QUEUED ─→ CLONING ─→ FIXING ─→ FAST_BUILD ─pass→ CANONICAL_BUILD ─pass→ PR_OPENED ─→ MERGED
 *                │         │          │ fail              │ fail
 *                │         │          ▼                   ▼
 *                │         │       (back to FIXING)    FAILED
 *                │         ▼
 *                │      SAFETY_BLOCKED ─(override)→ FAST_BUILD
 *                │
 *                └──→ FAILED
 *
 *   Any state ─→ ABORTED (manual)
 */
public final class FixRunStateMachine {

    private static final Map<FixRunState, Set<FixRunState>> TRANSITIONS = new EnumMap<>(FixRunState.class);

    static {
        TRANSITIONS.put(FixRunState.QUEUED, EnumSet.of(
                FixRunState.CLONING, FixRunState.FAILED, FixRunState.ABORTED));
        TRANSITIONS.put(FixRunState.CLONING, EnumSet.of(
                FixRunState.FIXING, FixRunState.FAILED, FixRunState.ABORTED));
        TRANSITIONS.put(FixRunState.FIXING, EnumSet.of(
                FixRunState.FAST_BUILD, FixRunState.SAFETY_BLOCKED, FixRunState.FAILED, FixRunState.ABORTED));
        TRANSITIONS.put(FixRunState.FAST_BUILD, EnumSet.of(
                FixRunState.CANONICAL_BUILD, FixRunState.FIXING /* refix loop */,
                FixRunState.FAILED, FixRunState.ABORTED));
        TRANSITIONS.put(FixRunState.CANONICAL_BUILD, EnumSet.of(
                FixRunState.PR_OPENED, FixRunState.FAILED, FixRunState.ABORTED));
        TRANSITIONS.put(FixRunState.PR_OPENED, EnumSet.of(
                FixRunState.MERGED, FixRunState.FAILED, FixRunState.ABORTED));
        TRANSITIONS.put(FixRunState.SAFETY_BLOCKED, EnumSet.of(
                FixRunState.FAST_BUILD /* user override */, FixRunState.ABORTED, FixRunState.FAILED));
        TRANSITIONS.put(FixRunState.MERGED, EnumSet.noneOf(FixRunState.class));   // terminal
        TRANSITIONS.put(FixRunState.FAILED, EnumSet.noneOf(FixRunState.class));   // terminal
        TRANSITIONS.put(FixRunState.ABORTED, EnumSet.noneOf(FixRunState.class));  // terminal
    }

    private FixRunStateMachine() {}

    public static boolean canTransition(FixRunState from, FixRunState to) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(FixRunState.class)).contains(to);
    }

    public static void requireTransition(FixRunState from, FixRunState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransition(from, to);
        }
    }

    public static boolean isTerminal(FixRunState state) {
        return TRANSITIONS.getOrDefault(state, EnumSet.noneOf(FixRunState.class)).isEmpty();
    }

    public static class IllegalStateTransition extends RuntimeException {
        public IllegalStateTransition(FixRunState from, FixRunState to) {
            super("Illegal FixRun transition: " + from + " → " + to);
        }
    }
}
