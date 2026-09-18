package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;

/**
 * End practice (legacy parity): fresh dragon fight, spawn platform provided
 * by the version's {@code resetFight}, teleport to the obsidian platform.
 * Completes when no living dragon remains in the practice world; completion
 * fires exactly once (the engine short-circuits finished sessions).
 */
public class EndScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("end");

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.END;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.END);
        context.adapter().dragons().resetFight(context.world());
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        teleportStart(context, new PracticePosition(100.0, 49.0, 0.0, 90.0f, 0.0f));
        applyLoadoutSetting(context);
    }

    @Override
    public TickResult tick(PracticeContext context) throws PracticeException {
        // The fresh fight spawns its dragon on a later tick, so "no living
        // dragon" only finishes after one was actually seen alive; otherwise
        // every attempt would complete before the dragon exists.
        if (context.world() == null) {
            return TickResult.continueTick();
        }
        if (context.adapter().dragons().hasLivingDragon(context.world())) {
            track(context, "seenAlive", Boolean.TRUE);
            return TickResult.continueTick();
        }
        if (Boolean.TRUE.equals(tracked(context, "seenAlive"))) {
            return TickResult.finished();
        }
        return TickResult.continueTick();
    }

    @Override
    public TickResult onEvent(PracticeContext context, PracticeEvent event) {
        if (event instanceof PracticeEvent.DragonKilledEvent && context.world() != null
                && context.world().handleId()
                        .equals(((PracticeEvent.DragonKilledEvent) event).worldHandle())) {
            return TickResult.finished();
        }
        return TickResult.continueTick();
    }

    @Override
    public void stop(PracticeContext context) {
        deleteWorldQuietly(context);
    }
}
