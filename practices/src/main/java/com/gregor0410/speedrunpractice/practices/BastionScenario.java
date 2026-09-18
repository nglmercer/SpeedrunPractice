package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.Capability;
import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.List;

/**
 * Bastion practice (new). Settings: {@code bastion.type} (housing, stables,
 * treasure, bridge, random), {@code spawn.mode} (outside, entrance, route,
 * random_exterior, portal_exit), {@code spawn.distance}, plus
 * {@code bastion.health} applied after the standard reset.
 */
public class BastionScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("bastion");

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
            target = choice.position().offset(distance, 0.0, 0.0);
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
        applyLoadoutSetting(context);
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
