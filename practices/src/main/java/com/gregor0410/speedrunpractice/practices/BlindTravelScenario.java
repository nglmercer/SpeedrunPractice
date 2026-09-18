package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Blind Travel practice (new). Seeded-random nether point at
 * {@code blind.targetDistance}; portal materials unless a loadout is set or
 * {@code blind.givePortalMats} is false.
 */
public class BlindTravelScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("blind_travel");

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.BLIND_TRAVEL;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.NETHER);
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        PracticePosition spawn = spawnOf(context);
        int distance = Math.max(0, context.settings().getInt("blind.targetDistance", 1000));
        Random random = new Random(context.seed());
        double angle = random.nextDouble() * Math.PI * 2.0;
        PracticePosition target = new PracticePosition(
                spawn.x() + Math.cos(angle) * distance, spawn.y(), spawn.z() + Math.sin(angle) * distance, 90.0f, 0.0f);
        teleportStart(context, target);
        if (context.settings().loadoutId() == null && context.settings().getBoolean("blind.givePortalMats", true)) {
            List<Loadout.Item> items = new ArrayList<Loadout.Item>();
            items.add(new Loadout.Item("minecraft:obsidian", 0, 10));
            items.add(new Loadout.Item("minecraft:flint_and_steel", 1, 1));
            items.add(new Loadout.Item("minecraft:bread", 2, 16));
            context.adapter().players().applyLoadout(context.player(), new Loadout("blind_travel_mats", items));
        } else {
            applyLoadoutSetting(context);
        }
    }

    @Override
    public TickResult tick(PracticeContext context) {
        return TickResult.continueTick();
    }

    @Override
    public void stop(PracticeContext context) {
        deleteWorldQuietly(context);
    }
}
