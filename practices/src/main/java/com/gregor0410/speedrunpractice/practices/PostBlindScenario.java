package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Post-blind practice (legacy parity): seeded-random point between
 * {@code postblind.minDist} and {@code postblind.maxDist} from the stronghold.
 * Eye count via {@code postblind.eyes} when no loadout is configured.
 */
public class PostBlindScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("postblind");

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.POSTBLIND;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.OVERWORLD);
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        PracticePosition spawn = spawnOf(context);
        int maxDist = Math.max(0, context.settings().getInt("postblind.maxDist", 1000));
        int minDist = Math.max(0, context.settings().getInt("postblind.minDist", 0));
        if (minDist > maxDist) {
            int swap = minDist;
            minDist = maxDist;
            maxDist = swap;
        }
        int radius = context.settings().getInt("postblind.searchRadius", 100000);
        Optional<StructureAdapter.StructureLocation> stronghold = locate(context, "stronghold", spawn, radius);
        PracticePosition target = spawn;
        if (stronghold.isPresent()) {
            Random random = new Random(context.seed());
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = maxDist == minDist ? maxDist : minDist + random.nextInt(maxDist - minDist + 1);
            PracticePosition center = stronghold.get().position();
            target = new PracticePosition(center.x() + Math.cos(angle) * distance, spawn.y(),
                    center.z() + Math.sin(angle) * distance, 90.0f, 0.0f);
        } else {
            SpeedrunLogger.warn("No stronghold in range; using world spawn");
        }
        teleportStart(context, target);
        if (context.settings().loadoutId() == null && context.settings().get("postblind.eyes") != null) {
            int eyes = Math.max(0, Math.min(64, context.settings().getInt("postblind.eyes", 12)));
            List<Loadout.Item> items = new ArrayList<Loadout.Item>();
            items.add(new Loadout.Item("minecraft:ender_eye", 5, eyes));
            items.add(new Loadout.Item("minecraft:ender_pearl", 4, 16));
            context.adapter().players().applyLoadout(context.player(), new Loadout("postblind_eyes", items));
        } else {
            applyLoadoutSetting(context);
        }
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
