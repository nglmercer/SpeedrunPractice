package com.gregor0410.speedrunpractice.common.api;

/**
 * The one lifecycle every practice uses (plan section 11). Scenarios must not
 * touch Minecraft internals; all world/player access goes through the
 * {@code PracticeContext} adapter.
 */
public interface PracticeScenario {
    /** Reset flavours supported by {@link #reset(PracticeContext, ResetMode)}. */
    enum ResetMode {
        SAME_SEED,
        NEW_SEED,
        PREVIOUS_SEED,
        CHECKPOINT,
        FULL_RESET
    }

    /** Per-tick outcome; the engine records time/stats when finished. */
    final class TickResult {
        private final boolean finished;

        private TickResult(boolean finished) {
            this.finished = finished;
        }

        public static TickResult continueTick() {
            return new TickResult(false);
        }

        public static TickResult finished() {
            return new TickResult(true);
        }

        public boolean isFinished() {
            return finished;
        }
    }

    PracticeId id();

    PracticeType type();

    void prepare(PracticeContext context) throws PracticeException;

    void start(PracticeContext context) throws PracticeException;

    TickResult tick(PracticeContext context) throws PracticeException;

    void reset(PracticeContext context, ResetMode mode) throws PracticeException;

    void stop(PracticeContext context) throws PracticeException;
}
