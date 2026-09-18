package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.Capability;
import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.List;

/**
 * Bastion practice (new). Settings: {@code bastion.type} (housing, stables,
 * treasure, bridge, random), {@code spawn.mode} (outside, entrance, route,
 * random_exterior, portal_exit), {@code spawn.distance}, plus
 * {@code bastion.health} and {@code bastion.food} applied after the standard
 * reset. {@code random_exterior} picks a seeded-random direction at
 * {@code spawn.distance} so the same seed always yields the same approach.
 *
 * <p>Completion ({@code bastion.goal}, default {@code obtain_target_pearls}):
 * holding {@code bastion.targetPearls} (default 16) ender pearls finishes;
 * {@code leave_bastion} / {@code reach_exit} finish after entering the
 * bastion area and leaving it again, {@code manual} never auto-finishes.
 */
public class BastionScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("bastion");

    /** Bastion subtypes the {@code bastion.type} setting accepts. */
    static final java.util.Set<String> KNOWN_TYPES =
            java.util.Collections.unmodifiableSet(new java.util.HashSet<String>(java.util.Arrays.asList(
                    "housing", "stables", "treasure", "bridge", "random")));

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.BASTION;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.NETHER);
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        PracticePosition spawn = spawnOf(context);
        String wanted = context.settings().getOrDefault("bastion.type", "random").trim().toLowerCase();
        String mode = context.settings().getOrDefault("spawn.mode", "outside").trim().toLowerCase();
        int distance = Math.max(0, context.settings().getInt("spawn.distance", 40));

        if (!KNOWN_TYPES.contains(wanted)) {
            SpeedrunLogger.warn("Unknown bastion.type \"" + wanted + "\"; using random");
            wanted = "random";
        }
        if (!context.adapter().supports(Capability.BASTION_TYPE_QUERY) && !"random".equals(wanted)) {
            SpeedrunLogger.warn("Bastion subtype detection is unavailable on "
                    + context.adapter().version().versionString() + "; type filter ignored");
            wanted = "random";
        }
        StructureAdapter.StructureQuery query = StructureAdapter.StructureQuery.builder("bastion_remnant")
                .center(spawn).radius(context.settings().getInt("spawn.radius", 10000)).build();
        List<StructureAdapter.StructureLocation> found = context.adapter().structures()
                .locate(context.world(), query, 10);
        if (found.isEmpty()) {
            throw new PracticeException("No bastion in range on seed " + context.seed(),
                    "Unable to start Bastion Practice: no bastion was found on this seed. Try a new seed.");
        }
        StructureAdapter.StructureLocation choice = found.get(0);
        if (!"random".equals(wanted)) {
            for (StructureAdapter.StructureLocation candidate : found) {
                if (wanted.equalsIgnoreCase(candidate.metadata().get("bastion.type"))) {
                    choice = candidate;
                    break;
                }
            }
            if (!wanted.equalsIgnoreCase(choice.metadata().get("bastion.type"))) {
                SpeedrunLogger.warn("No " + wanted + " bastion nearby; using a "
                        + choice.metadata().get("bastion.type"));
            }
        }
        track(context, "target", choice.position());
        PracticePosition target;
        if ("entrance".equals(mode) || "route".equals(mode)) {
            target = choice.position();
        } else if ("portal_exit".equals(mode)) {
            target = spawn;
        } else {
            if (!"outside".equals(mode) && !"random_exterior".equals(mode)) {
                SpeedrunLogger.warn("Unknown bastion spawn.mode \"" + mode + "\"; using outside");
            }
            // Versions clamp Y to safe ground on teleport.
            double dx = distance;
            double dz = 0.0;
            if ("random_exterior".equals(mode)) {
                double angle = new java.util.Random(context.seed()).nextDouble() * Math.PI * 2.0;
                dx = Math.cos(angle) * distance;
                dz = Math.sin(angle) * distance;
            }
            target = choice.position().offset(dx, 0.0, dz);
        }
        teleportStart(context, target);
        String health = context.settings().get("bastion.health");
        if (health != null) {
            try {
                context.adapter().players().setHealth(context.player(), Double.parseDouble(health.trim()));
            } catch (NumberFormatException bad) {
                SpeedrunLogger.warn("Ignoring bad bastion.health \"" + health + "\"");
            }
        }
        String food = context.settings().get("bastion.food");
        if (food != null) {
            try {
                int foodLevel = Math.max(0, Math.min(20, Integer.parseInt(food.trim())));
                context.adapter().players().setFood(context.player(), foodLevel);
            } catch (NumberFormatException bad) {
                SpeedrunLogger.warn("Ignoring bad bastion.food \"" + food + "\"");
            }
        }
        applyLoadoutSetting(context);
    }

    @Override
    public TickResult tick(PracticeContext context) {
        String goal = context.settings().getOrDefault("bastion.goal", "obtain_target_pearls")
                .trim().toLowerCase();
        if ("obtain_target_pearls".equals(goal)) {
            int target = Math.max(1, context.settings().getInt("bastion.targetPearls", 16));
            if (countLiveItem(context, "minecraft:ender_pearl") >= target) {
                return TickResult.finished();
            }
        } else if ("leave_bastion".equals(goal) || "reach_exit".equals(goal)) {
            PracticePosition bastion = tracked(context, "target");
            if (bastion == null) {
                return TickResult.continueTick();
            }
            double radius = Math.max(0, context.settings().getInt("bastion.completeRadius", 32));
            if (reached(context, bastion, radius)) {
                track(context, "entered", Boolean.TRUE);
            } else if (Boolean.TRUE.equals(tracked(context, "entered"))) {
                return TickResult.finished();
            }
        } else if (!"manual".equals(goal)) {
            warnOnce(context, "goal", "Unknown bastion.goal \"" + goal + "\"; never auto-finishing");
        }
        return TickResult.continueTick();
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
