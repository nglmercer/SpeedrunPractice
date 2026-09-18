package com.gregor0410.speedrunpractice.common.api;

/** Outcome of one practice attempt, built by the engine when a tick finishes. */
public final class PracticeResult {
    /** How the attempt ended. */
    public enum Status {
        COMPLETED,
        RESET,
        ABANDONED,
        FAILED
    }

    private final PracticeId practice;
    private final long seed;
    private final long elapsedMs;
    private final Status status;

    public PracticeResult(PracticeId practice, long seed, long elapsedMs, Status status) {
        if (practice == null || status == null) {
            throw new IllegalArgumentException("practice and status must not be null");
        }
        this.practice = practice;
        this.seed = seed;
        this.elapsedMs = Math.max(0L, elapsedMs);
        this.status = status;
    }

    public PracticeId practice() {
        return practice;
    }

    public long seed() {
        return seed;
    }

    public long elapsedMs() {
        return elapsedMs;
    }

    public Status status() {
        return status;
    }

    @Override
    public String toString() {
        return "PracticeResult(" + practice + ", seed=" + seed + ", " + elapsedMs + "ms, " + status + ")";
    }
}
