package com.gregor0410.speedrunpractice.common.api;

/** Mutable run state for one active practice attempt. */
public final class PracticeSession {
    private final PracticeId practiceId;
    private final long startedAtMs;
    private PracticeState state = PracticeState.IDLE;
    private long seed;
    private int attemptCount;

    public PracticeSession(PracticeId practiceId) {
        this(practiceId, 0L);
    }

    public PracticeSession(PracticeId practiceId, long seed) {
        if (practiceId == null) {
            throw new IllegalArgumentException("practiceId must not be null");
        }
        this.practiceId = practiceId;
        this.seed = seed;
        this.startedAtMs = System.currentTimeMillis();
    }

    public PracticeId practiceId() {
        return practiceId;
    }

    public PracticeState state() {
        return state;
    }

    public long seed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public void countAttempt() {
        attemptCount++;
    }

    /**
     * Guarded transition. Legal paths: IDLE -> PREPARING -> RUNNING ->
     * COMPLETED/STOPPED, RUNNING <-> PAUSED, PAUSED -> STOPPED.
     */
    public void transitionTo(PracticeState next) {
        if (next == null) {
            throw new IllegalArgumentException("next state must not be null");
        }
        boolean legal;
        switch (state) {
            case IDLE:
                legal = next == PracticeState.PREPARING || next == PracticeState.STOPPED;
                break;
            case PREPARING:
                legal = next == PracticeState.RUNNING || next == PracticeState.STOPPED;
                break;
            case RUNNING:
                legal = next == PracticeState.PAUSED || next == PracticeState.COMPLETED
                        || next == PracticeState.STOPPED || next == PracticeState.PREPARING;
                break;
            case PAUSED:
                legal = next == PracticeState.RUNNING || next == PracticeState.STOPPED
                        || next == PracticeState.PREPARING;
                break;
            case COMPLETED:
            case STOPPED:
                legal = next == PracticeState.PREPARING || next == PracticeState.IDLE;
                break;
            default:
                legal = false;
                break;
        }
        if (!legal) {
            throw new IllegalStateException("Illegal session transition " + state + " -> " + next);
        }
        state = next;
    }
}
