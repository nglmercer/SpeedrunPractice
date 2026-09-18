package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutManager;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.Optional;

/** Shared helpers so scenarios stay small and adapter-only. */
public abstract class AbstractPracticeScenario implements PracticeScenario {
    @Override
    public void reset(PracticeContext context, ResetMode mode) throws PracticeException {
        // The engine sets the new seed on the context first (except CHECKPOINT,
        // which it handles itself), so every mode rebuilds from context state.
        stop(context);
        prepare(context);
        start(context);
    }

    protected WorldAdapter.PracticeWorldOptions options(PracticeDimension dimension, PracticeContext context) {
        return WorldAdapter.PracticeWorldOptions.builder(dimension)
                .generateStructures(context.settings().getBoolean("world.generateStructures", true))
                .bonusChest(context.settings().getBoolean("world.bonusChest", false))
                .build();
    }

    protected PracticeWorld createWorld(PracticeContext context, PracticeDimension dimension) throws PracticeException {
        PracticeWorld world = context.adapter().worlds().createPracticeWorld(context.seed(), options(dimension, context));
        context.setWorld(world);
        return world;
    }

    protected void deleteWorldQuietly(PracticeContext context) {
        if (context.world() == null) {
            return;
        }
        try {
            context.adapter().worlds().deletePracticeWorld(context.world());
        } catch (PracticeException failure) {
            SpeedrunLogger.warn("Could not delete practice world: " + failure.getUserMessage());
        } finally {
            context.setWorld(null);
        }
    }

    protected LoadoutManager loadouts(PracticeContext context) {
        return context.getAttribute(ScenarioEngine.LOADOUTS_ATTRIBUTE);
    }

    /** Applies the {@code loadout} setting; missing setting means "keep current". */
    protected void applyLoadoutSetting(PracticeContext context) throws PracticeException {
        String id = context.settings().loadoutId();
        if (id == null) {
            return;
        }
        LoadoutManager manager = loadouts(context);
        if (manager == null) {
            SpeedrunLogger.warn("No loadout manager installed; skipping loadout \"" + id + "\"");
            return;
        }
        Loadout loadout = manager.get(id);
        if (loadout == null) {
            throw new PracticeException("Unknown loadout: " + id,
                    "Loadout \"" + id + "\" does not exist. Check the scenario screen.");
        }
        context.adapter().players().applyLoadout(context.player(), loadout);
    }

    protected PracticePosition spawnOf(PracticeContext context) throws PracticeException {
        return context.adapter().worlds().spawnPosition(context.world());
    }

    protected Optional<StructureAdapter.StructureLocation> locate(PracticeContext context, String structureId,
                                                                 PracticePosition center, int radius)
            throws PracticeException {
        StructureAdapter.StructureQuery query = StructureAdapter.StructureQuery.builder(structureId)
                .center(center).radius(radius).build();
        return context.adapter().structures().locateNearest(context.world(), query);
    }

    /** Teleports and applies the full player reset (legacy resetPlayer parity). */
    protected void teleportStart(PracticeContext context, PracticePosition position) throws PracticeException {
        context.adapter().players().teleport(context.player(), position);
        context.adapter().players().resetPlayer(context.player());
    }
}
