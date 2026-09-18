package com.gregor0410.speedrunpractice.common.timer;

/**
 * Internal timer abstraction (plan section 24). Monotonic; SpeedRunIGT is an
 * optional mirror via {@code TimerAdapter}, never a requirement.
 */
public interface PracticeTimer {
    /** When timing begins. */
    enum StartCondition {
        SCENARIO_LOAD,
        FIRST_MOVEMENT,
        DIMENSION_ENTRY,
        PORTAL_EXIT,
        MANUAL
    }

    /** When timing ends. */
    enum StopCondition {
        COMPLETION,
        DIMENSION_ENTRY,
        STRUCTURE_REACHED,
        DRAGON_DEATH,
        MANUAL
    }

    void start();

    void stop();

    void pause();

    void resume();

    void reset();

    long elapsedMs();

    boolean isRunning();

    /** Restores an elapsed value (checkpoints); keeps the running state otherwise. */
    void setElapsedMs(long elapsedMs);

    StartCondition getStartCondition();

    void setStartCondition(StartCondition condition);

    StopCondition getStopCondition();

    void setStopCondition(StopCondition condition);
}
