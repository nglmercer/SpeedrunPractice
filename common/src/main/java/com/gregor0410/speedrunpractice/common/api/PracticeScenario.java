package com.gregor0410.speedrunpractice.common.api;

import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;

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

    /**
     * Handles one shared event (plan section 21). The default ignores it;
     * scenarios override to finish early (dragon kill, portal exit) or track
     * progress. Returning {@link TickResult#finished()} completes the attempt
     * exactly like a finishing {@link #tick(PracticeContext)}.
     */
    default TickResult onEvent(PracticeContext context, PracticeEvent event) throws PracticeException {
        return TickResult.continueTick();
    }

    /**
     * Captures scenario-internal state for checkpoints (plan section 39:
     * target structure/chest/stronghold, entered flags, progress). Null when
     * the scenario keeps no extra state.
     */
    default PracticeCheckpoint.ScenarioSnapshot captureState(PracticeContext context)
            throws PracticeException {
        return null;
    }

    /**
     * Restores state previously captured by
     * {@link #captureState(PracticeContext)}. Null snapshots are ignored.
     */
    default void restoreState(PracticeContext context, PracticeCheckpoint.ScenarioSnapshot snapshot)
            throws PracticeException {
    }
}
