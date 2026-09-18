package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.Optional;

/**
 * Buried Treasure practice (legacy parity): spawns near the nearest
 * treasure (offset by {@code buried_treasure.spawnOffset}, default 12
 * blocks), else world spawn when none is found in range.
 *
 * <p>Completion: reaching the located chest within
 * {@code buried_treasure.completeRadius} (default 4 blocks). The radius is
 * deliberately tight — arriving in the chunk is not enough. Detecting the
 * actual chest opening needs a version inventory event and stays pending;
 * until then proximity to the real chest position finishes the attempt. When
 * no chest was found at start there is no target and the attempt never
 * auto-finishes.
 */
public class BuriedTreasureScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("buried_treasure");

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.BURIED_TREASURE;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.OVERWORLD);
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        PracticePosition spawn = spawnOf(context);
        int radius = context.settings().getInt("spawn.radius", 10000);
        Optional<StructureAdapter.StructureLocation> found = locate(context, "buried_treasure", spawn, radius);
        PracticePosition target = spawn;
        if (found.isPresent()) {
            PracticePosition chest = found.get().position();
            track(context, "target", chest);
            int offset = Math.max(0, context.settings().getInt("buried_treasure.spawnOffset", 12));
            target = chest.offset(offset, 0.0, 0.0);
        } else {
            SpeedrunLogger.warn("No buried treasure in range; using world spawn");
        }
        teleportStart(context, target);
        applyLoadoutSetting(context);
    }

    @Override
    public TickResult tick(PracticeContext context) {
        String goal = context.settings().getOrDefault("buried_treasure.goal", "chest").trim().toLowerCase();
        if (!"manual".equals(goal) && !"chest".equals(goal)) {
            warnOnce(context, "goal", "Unknown buried_treasure.goal \"" + goal + "\"; using chest");
        }
        if ("manual".equals(goal)) {
            return TickResult.continueTick();
        }
        PracticePosition target = tracked(context, "target");
        if (target == null) {
            return TickResult.continueTick();
        }
        double radius = Math.max(0, context.settings().getInt("buried_treasure.completeRadius", 4));
        if (reached(context, target, radius)) {
            return TickResult.finished();
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
