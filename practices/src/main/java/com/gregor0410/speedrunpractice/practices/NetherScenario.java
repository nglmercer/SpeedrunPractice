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
 * Nether practice (legacy parity): portal-exit start by default, or
 * {@code spawn.structure} (e.g. bastion_remnant, fortress) for locating and
 * route practice.
 *
 * <p>Completion ({@code nether.goal}, default {@code exit_nether}):
 * {@code reach_bastion} / {@code reach_fortress} finish near the structure,
 * {@code obtain_pearls} / {@code obtain_blaze_rods} on the configured counts
 * ({@code nether.targetPearls}, {@code nether.targetRods}),
 * {@code exit_nether} on leaving the Nether, {@code manual} never
 * auto-finishes.
 */
public class NetherScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("nether");

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.NETHER;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.NETHER);
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
                SpeedrunLogger.warn("Structure \"" + structure + "\" not found in range; using nether spawn");
            }
        }
        teleportStart(context, target);
        applyLoadoutSetting(context);
    }

    @Override
    public TickResult tick(PracticeContext context) {
        String goal = context.settings().getOrDefault("nether.goal", "exit_nether").trim().toLowerCase();
        if ("exit_nether".equals(goal)) {
            PracticeDimension dimension = currentDimension(context);
            if (dimension != null && dimension != PracticeDimension.NETHER) {
                return TickResult.finished();
            }
        } else if ("reach_bastion".equals(goal) || "reach_fortress".equals(goal)) {
            String structure = "reach_bastion".equals(goal) ? "bastion_remnant" : "fortress";
            double radius = Math.max(0, context.settings().getInt("nether.completeRadius", 32));
            if (reached(context, structureTarget(context, structure), radius)) {
                return TickResult.finished();
            }
        } else if ("obtain_pearls".equals(goal)) {
            int target = Math.max(1, context.settings().getInt("nether.targetPearls", 16));
            if (countLiveItem(context, "minecraft:ender_pearl") >= target) {
                return TickResult.finished();
            }
        } else if ("obtain_blaze_rods".equals(goal)) {
            int target = Math.max(1, context.settings().getInt("nether.targetRods", 8));
            if (countLiveItem(context, "minecraft:blaze_rod") >= target) {
                return TickResult.finished();
            }
        } else if (!"manual".equals(goal)) {
            warnOnce(context, "goal", "Unknown nether.goal \"" + goal + "\"; never auto-finishing");
        }
        return TickResult.continueTick();
    }

    @Override
    public TickResult onEvent(PracticeContext context, PracticeEvent event) {
        String goal = context.settings().getOrDefault("nether.goal", "exit_nether").trim().toLowerCase();
        if ("exit_nether".equals(goal) && event instanceof PracticeEvent.DimensionChangedEvent) {
            PracticeDimension to = ((PracticeEvent.DimensionChangedEvent) event).to();
            if (to != null && to != PracticeDimension.NETHER) {
                return TickResult.finished();
            }
        }
        return TickResult.continueTick();
    }

    private PracticePosition structureTarget(PracticeContext context, String structure) {
        PracticePosition cached = tracked(context, "target");
        if (cached != null) {
            return cached;
        }
        try {
            Optional<StructureAdapter.StructureLocation> found = locate(context, structure,
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
