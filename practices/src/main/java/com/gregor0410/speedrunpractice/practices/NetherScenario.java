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
 * Nether practice (legacy parity): portal-exit start by default, or
 * {@code spawn.structure} (e.g. bastion_remnant, fortress) for locating and
 * route practice.
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
        return TickResult.continueTick();
    }

    @Override
    public void stop(PracticeContext context) {
        deleteWorldQuietly(context);
    }
}
