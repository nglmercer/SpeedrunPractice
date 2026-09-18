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

    /**
     * Parses a {@code timer.start} setting ({@code scenario_load},
     * {@code player_move}, {@code dimension_entry}, {@code portal_exit},
     * {@code manual}). Null when blank or unknown; callers fall back to
     * {@link StartCondition#SCENARIO_LOAD}.
     */
    static StartCondition parseStart(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase();
        if (normalized.isEmpty() || "scenario_load".equals(normalized)) {
            return normalized.isEmpty() ? null : StartCondition.SCENARIO_LOAD;
        } else if ("player_move".equals(normalized) || "first_movement".equals(normalized)) {
            return StartCondition.FIRST_MOVEMENT;
        } else if ("dimension_entry".equals(normalized)) {
            return StartCondition.DIMENSION_ENTRY;
        } else if ("portal_exit".equals(normalized)) {
            return StartCondition.PORTAL_EXIT;
        } else if ("manual".equals(normalized)) {
            return StartCondition.MANUAL;
        }
        return null;
    }

    /**
     * Parses a {@code timer.stop} setting ({@code scenario_complete},
     * {@code dimension_entry}, {@code structure_reached},
     * {@code dragon_death}, {@code manual}). Null when blank or unknown;
     * callers fall back to {@link StopCondition#COMPLETION}.
     */
    static StopCondition parseStop(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase();
        if (normalized.isEmpty() || "scenario_complete".equals(normalized)
                || "completion".equals(normalized)) {
            return normalized.isEmpty() ? null : StopCondition.COMPLETION;
        } else if ("dimension_entry".equals(normalized)) {
            return StopCondition.DIMENSION_ENTRY;
        } else if ("structure_reached".equals(normalized)) {
            return StopCondition.STRUCTURE_REACHED;
        } else if ("dragon_death".equals(normalized)) {
            return StopCondition.DRAGON_DEATH;
        } else if ("manual".equals(normalized)) {
            return StopCondition.MANUAL;
        }
        return null;
    }
}
