package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.Optional;

/**
 * Fortress practice (new). {@code fortress.mode}: find (start outside),
 * enter, blaze, navigation, exit (start inside).
 */
public class FortressScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("fortress");

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.FORTRESS;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.NETHER);
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        PracticePosition spawn = spawnOf(context);
        String mode = context.settings().getOrDefault("fortress.mode", "find").trim().toLowerCase();
        int radius = context.settings().getInt("spawn.radius", 10000);
        Optional<StructureAdapter.StructureLocation> found = locate(context, "fortress", spawn, radius);
        if (!found.isPresent()) {
            throw new PracticeException("No fortress in range on seed " + context.seed(),
                    "Unable to start Fortress Practice: no fortress was found on this seed. Try a new seed.");
        }
        PracticePosition target;
        if ("find".equals(mode)) {
            int distance = Math.max(0, context.settings().getInt("spawn.distance", 64));
            target = found.get().position().offset(distance, 0.0, 0.0);
        } else if ("enter".equals(mode) || "blaze".equals(mode) || "navigation".equals(mode) || "exit".equals(mode)) {
            target = found.get().position();
        } else {
            SpeedrunLogger.warn("Unknown fortress.mode \"" + mode + "\"; starting at the fortress");
            target = found.get().position();
        }
        teleportStart(context, target);
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
