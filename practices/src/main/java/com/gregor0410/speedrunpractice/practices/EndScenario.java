package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;

/**
 * End practice (legacy parity): fresh dragon fight, spawn platform provided
 * by the version's {@code resetFight}, teleport to the obsidian platform.
 * Completes when no living dragon remains.
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
        if (context.world() != null && !context.adapter().dragons().hasLivingDragon(context.world())) {
            return TickResult.finished();
        }
        return TickResult.continueTick();
    }

    @Override
    public void stop(PracticeContext context) {
        deleteWorldQuietly(context);
    }
}
