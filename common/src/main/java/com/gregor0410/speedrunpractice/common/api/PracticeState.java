package com.gregor0410.speedrunpractice.common.api;

/**
 * Lifecycle state of a {@link PracticeSession} (plan section 90).
 * {@code STOPPING} covers teardown between the stop request and the final
 * state; {@code FAILED} marks a start/reset that threw after mutating game
 * state, so callers can distinguish it from a clean stop.
 */
public enum PracticeState {
    IDLE,
    PREPARING,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPING,
    STOPPED,
    FAILED
}
