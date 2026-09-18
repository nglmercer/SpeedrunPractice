package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.Capability;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * One Cycle practice (new): End setup with a cycling loadout and a forced
 * perch where the version supports {@code DRAGON_FORCE_PERCH}.
 */
public class OneCycleScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("onecycle");

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.ONE_CYCLE;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.END);
        context.adapter().dragons().resetFight(context.world());
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        teleportStart(context, new PracticePosition(100.0, 49.0, 0.0, 90.0f, 0.0f));
        if (context.settings().loadoutId() == null) {
            applyCompatibleLoadout(context, defaultCycleLoadout());
        } else {
            applyLoadoutSetting(context);
        }
        if (context.adapter().supports(Capability.DRAGON_FORCE_PERCH)) {
            context.adapter().dragons().forcePerch(context.world());
        } else {
            SpeedrunLogger.warn("Forced perch is unavailable on "
                    + context.adapter().version().versionString() + "; wait for a natural perch");
        }
    }

    private Loadout defaultCycleLoadout() {
        List<Loadout.Item> items = new ArrayList<Loadout.Item>();
        items.add(new Loadout.Item("minecraft:iron_axe", 0, 1));
        // Bed layout matches the legacy End inventory (hotbar slot plus rows).
        items.add(new Loadout.Item("minecraft:white_bed", 1, 1));
        for (int slot = 9; slot <= 17; slot++) {
            items.add(new Loadout.Item("minecraft:white_bed", slot, 1));
        }
        items.add(new Loadout.Item("minecraft:iron_pickaxe", 2, 1));
        items.add(new Loadout.Item("minecraft:water_bucket", 3, 1));
        items.add(new Loadout.Item("minecraft:ender_pearl", 4, 4));
        items.add(new Loadout.Item("minecraft:respawn_anchor", 5, 4));
        items.add(new Loadout.Item("minecraft:glowstone", 6, 4));
        items.add(new Loadout.Item("minecraft:crying_obsidian", 7, 64));
        items.add(new Loadout.Item("minecraft:cobblestone", 8, 64));
        items.add(new Loadout.Item("minecraft:bow", 27, 1));
        items.add(new Loadout.Item("minecraft:arrow", 28, 64));
        return new Loadout("onecycle_default", items);
    }

    @Override
    public TickResult tick(PracticeContext context) throws PracticeException {
        // Same seen-alive gate as End: the dragon spawns after setup, so
        // only a dragon that lived and died finishes the attempt.
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
