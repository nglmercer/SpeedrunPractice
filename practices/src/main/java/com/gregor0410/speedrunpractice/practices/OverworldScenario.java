package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.Optional;

/**
 * Overworld practice (legacy parity): world spawn, or {@code spawn.structure}
 * for village / shipwreck / buried treasure / lava pool / portal entry /
 * custom structure starts.
 *
 * <p>Completion ({@code overworld.goal}, default {@code enter_nether}):
 * entering the Nether finishes; {@code reach_structure} finishes near the
 * {@code spawn.structure} target, {@code obtain_item} on holding
 * {@code overworld.item} x {@code overworld.count}, {@code manual} never
 * auto-finishes.
 */
public class OverworldScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("overworld");

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.OVERWORLD;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.OVERWORLD);
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        PracticePosition spawn = spawnOf(context);
        PracticePosition target = spawn;
        String structure = context.settings().get("spawn.structure");
        if (structure != null && !structure.trim().isEmpty()) {
            int radius = context.settings().getInt("spawn.radius", 10000);
            Optional<StructureAdapter.StructureLocation> found = locate(context, structure.trim(), spawn, radius);
            if (found.isPresent()) {
                target = found.get().position();
            } else {
                SpeedrunLogger.warn("Structure \"" + structure + "\" not found in range; using world spawn");
            }
        }
        teleportStart(context, target);
        applyLoadoutSetting(context);
    }

    @Override
    public TickResult tick(PracticeContext context) {
        String goal = context.settings().getOrDefault("overworld.goal", "enter_nether").trim().toLowerCase();
        if ("enter_nether".equals(goal)) {
            PracticeDimension dimension = currentDimension(context);
            if (dimension != null && dimension != PracticeDimension.OVERWORLD) {
                return TickResult.finished();
            }
        } else if ("reach_structure".equals(goal)) {
            PracticePosition target = reachTarget(context);
            double radius = Math.max(0, context.settings().getInt("overworld.completeRadius", 8));
            if (reached(context, target, radius)) {
                return TickResult.finished();
            }
        } else if ("obtain_item".equals(goal)) {
            String item = context.settings().get("overworld.item");
            if (item == null || item.trim().isEmpty()) {
                warnOnce(context, "goal", "overworld.goal \"obtain_item\" needs overworld.item; never auto-finishing");
            } else {
                int count = Math.max(1, context.settings().getInt("overworld.count", 1));
                if (countLiveItem(context, item.trim()) >= count) {
                    return TickResult.finished();
                }
            }
        } else if (!"manual".equals(goal)) {
            warnOnce(context, "goal", "Unknown overworld.goal \"" + goal + "\"; never auto-finishing");
        }
        return TickResult.continueTick();
    }

    @Override
    public TickResult onEvent(PracticeContext context, PracticeEvent event) {
        String goal = context.settings().getOrDefault("overworld.goal", "enter_nether").trim().toLowerCase();
        if ("enter_nether".equals(goal) && event instanceof PracticeEvent.DimensionChangedEvent) {
            PracticeDimension to = ((PracticeEvent.DimensionChangedEvent) event).to();
            if (to != null && to != PracticeDimension.OVERWORLD) {
                return TickResult.finished();
            }
        }
        return TickResult.continueTick();
    }

    private PracticePosition reachTarget(PracticeContext context) {
        PracticePosition cached = tracked(context, "target");
        if (cached != null) {
            return cached;
        }
        String structure = context.settings().get("spawn.structure");
        if (structure == null || structure.trim().isEmpty()) {
            warnOnce(context, "goal", "overworld.goal \"reach_structure\" needs spawn.structure;"
                    + " never auto-finishing");
            return null;
        }
        try {
            Optional<StructureAdapter.StructureLocation> found = locate(context, structure.trim(),
                    context.adapter().players().getPosition(context.player()),
                    context.settings().getInt("spawn.radius", 10000));
            if (found.isPresent()) {
                track(context, "target", found.get().position());
                return found.get().position();
            }
            return null;
        } catch (PracticeException failure) {
            return null;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    @Override
    public PracticeCheckpoint.ScenarioSnapshot captureState(PracticeContext context) {
        return captureTrackedState(context);
    }

    @Override
    public void restoreState(PracticeContext context, PracticeCheckpoint.ScenarioSnapshot snapshot) {
        restoreTrackedState(context, snapshot);
    }

    @Override
    public void stop(PracticeContext context) {
        deleteWorldQuietly(context);
    }
}
