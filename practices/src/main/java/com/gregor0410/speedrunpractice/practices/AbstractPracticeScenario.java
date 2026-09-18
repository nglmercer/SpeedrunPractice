package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutCompatibility;
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

    /**
     * Applies the {@code loadout} setting; missing setting means "keep
     * current". Unknown loadout ids fail readably; unknown item ids inside a
     * known preset are skipped with a warning (plan section 66) instead of
     * crashing the practice.
     */
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
        applyCompatibleLoadout(context, loadout);
    }

    /**
     * Applies a preset after filtering it for the version registry: unknown
     * items are skipped with a warning and counts clamped to the version's
     * max stack size, so one missing item degrades the kit instead of
     * crashing the practice (plan section 66).
     */
    protected void applyCompatibleLoadout(PracticeContext context, Loadout loadout) throws PracticeException {
        LoadoutCompatibility.Result filtered =
                LoadoutCompatibility.filter(context.adapter().registries(), loadout);
        for (String warning : filtered.warnings()) {
            SpeedrunLogger.warn(warning);
        }
        context.adapter().players().applyLoadout(context.player(), filtered.loadout());
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

    /**
     * The dimension the player is currently standing in. Null when the
     * adapter cannot answer (no world yet); completion checks treat that as
     * "not finished", never as an error.
     */
    protected PracticeDimension currentDimension(PracticeContext context) {
        try {
            PracticeWorld world = context.adapter().players().getWorld(context.player());
            return world == null ? null : world.dimension();
        } catch (PracticeException failure) {
            return null;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    /** True when the player is within {@code radius} blocks of {@code target}. */
    protected boolean reached(PracticeContext context, PracticePosition target, double radius) {
        if (target == null) {
            return false;
        }
        try {
            PracticePosition position = context.adapter().players().getPosition(context.player());
            return position != null && position.distanceTo(target) <= radius;
        } catch (PracticeException failure) {
            return false;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /**
     * Counts an item across the live inventory. Returns 0 when the inventory
     * cannot be captured; completion checks treat that as "not finished".
     */
    protected int countLiveItem(PracticeContext context, String itemId) {
        if (itemId == null) {
            return 0;
        }
        try {
            Loadout captured = context.adapter().inventories().captureLoadout(context.player(), "completion");
            if (captured == null) {
                return 0;
            }
            int total = 0;
            for (Loadout.Item item : captured.items()) {
                if (itemId.equalsIgnoreCase(item.itemId())) {
                    total += item.count();
                }
            }
            return total;
        } catch (PracticeException failure) {
            return 0;
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    /** Stores a completion-tracking value (target position, entered flag) on the context. */
    protected void track(PracticeContext context, String key, Object value) {
        context.setAttribute(getClass().getSimpleName() + "." + key, value);
    }

    /** Reads a value previously stored with {@link #track(PracticeContext, String, Object)}. */
    @SuppressWarnings("unchecked")
    protected <T> T tracked(PracticeContext context, String key) {
        return (T) context.getAttribute(getClass().getSimpleName() + "." + key);
    }

    /** Logs a tick-path warning at most once per run (avoids per-tick log spam). */
    protected void warnOnce(PracticeContext context, String key, String message) {
        String flag = "warned-" + key;
        if (!Boolean.TRUE.equals(tracked(context, flag))) {
            track(context, flag, Boolean.TRUE);
            SpeedrunLogger.warn(message);
        }
    }

    /**
     * Captures the standard tracked state ({@code target} position, the
     * {@code entered} flag and the {@code portalRoom} string) for
     * checkpoints (plan section 39).
     */
    protected PracticeCheckpoint.ScenarioSnapshot captureTrackedState(PracticeContext context) {
        java.util.Map<String, String> data = new java.util.LinkedHashMap<String, String>();
        PracticePosition target = tracked(context, "target");
        if (target != null) {
            data.put("target", target.x() + "," + target.y() + "," + target.z());
        }
        if (Boolean.TRUE.equals(tracked(context, "entered"))) {
            data.put("entered", "true");
        }
        Object portalRoom = tracked(context, "portalRoom");
        if (portalRoom instanceof String) {
            data.put("portalRoom", (String) portalRoom);
        }
        return new PracticeCheckpoint.MapScenarioSnapshot(id().value(), data);
    }

    /**
     * Restores state captured by {@link #captureTrackedState(PracticeContext)}.
     * Corrupt or foreign snapshots are ignored so a bad checkpoint can never
     * break the run.
     */
    protected void restoreTrackedState(PracticeContext context,
            PracticeCheckpoint.ScenarioSnapshot snapshot) {
        if (snapshot == null || snapshot.data() == null) {
            return;
        }
        String raw = snapshot.data().get("target");
        if (raw != null) {
            String[] parts = raw.split(",");
            if (parts.length == 3) {
                try {
                    track(context, "target", new PracticePosition(Double.parseDouble(parts[0].trim()),
                            Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim())));
                } catch (NumberFormatException bad) {
                    // Ignore corrupt coordinates; the run continues untracked.
                }
            }
        }
        if ("true".equals(snapshot.data().get("entered"))) {
            track(context, "entered", Boolean.TRUE);
        }
        if (snapshot.data().get("portalRoom") != null) {
            track(context, "portalRoom", snapshot.data().get("portalRoom"));
        }
    }
}
