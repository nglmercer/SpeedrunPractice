package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
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
 *
 * <p>Completion ({@code postblind.goal}, default {@code reach_stronghold}):
 * reaching the attempt's stronghold within
 * {@code postblind.completeRadius} (default 32); {@code enter_stronghold}
 * and {@code reach_portal_room} use tighter radii against the stairs or the
 * {@code portal_room} metadata when the version provides it;
 * {@code manual} never auto-finishes.
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
            track(context, "target", stronghold.get().position());
            String portalRoom = stronghold.get().metadata().get("portal_room");
            if (portalRoom != null) {
                track(context, "portalRoom", portalRoom);
            }
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
            applyCompatibleLoadout(context, new Loadout("postblind_eyes", items));
        } else {
            applyLoadoutSetting(context);
        }
    }

    @Override
    public TickResult tick(PracticeContext context) {
        String goal = context.settings().getOrDefault("postblind.goal", "reach_stronghold")
                .trim().toLowerCase();
        if ("manual".equals(goal)) {
            return TickResult.continueTick();
        }
        PracticePosition stronghold = tracked(context, "target");
        if (stronghold == null) {
            return TickResult.continueTick();
        }
        if ("reach_stronghold".equals(goal)) {
            double radius = Math.max(0, context.settings().getInt("postblind.completeRadius", 32));
            if (reached(context, stronghold, radius)) {
                return TickResult.finished();
            }
        } else if ("enter_stronghold".equals(goal)) {
            double radius = Math.max(0, context.settings().getInt("postblind.enterRadius", 8));
            if (reached(context, stronghold, radius)) {
                return TickResult.finished();
            }
        } else if ("reach_portal_room".equals(goal)) {
            double radius = Math.max(0, context.settings().getInt("postblind.portalRadius", 8));
            PracticePosition portalRoom = parsePortalRoom(tracked(context, "portalRoom"));
            if (reached(context, portalRoom == null ? stronghold : portalRoom, radius)) {
                return TickResult.finished();
            }
        } else {
            warnOnce(context, "goal", "Unknown postblind.goal \"" + goal + "\"; never auto-finishing");
        }
        return TickResult.continueTick();
    }

    private PracticePosition parsePortalRoom(Object raw) {
        if (!(raw instanceof String)) {
            return null;
        }
        String[] parts = ((String) raw).split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new PracticePosition(Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()));
        } catch (NumberFormatException bad) {
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
