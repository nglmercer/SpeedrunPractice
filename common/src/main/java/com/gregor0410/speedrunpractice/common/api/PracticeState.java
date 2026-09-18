package com.gregor0410.speedrunpractice.common.api;

/** Lifecycle state of a {@link PracticeSession}. */
public enum PracticeState {
    IDLE,
    PREPARING,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPED
}
