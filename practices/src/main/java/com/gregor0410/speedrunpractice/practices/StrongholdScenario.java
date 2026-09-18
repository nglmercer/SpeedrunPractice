package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stronghold practice (legacy parity). {@code stronghold.mode}: entry,
 * navigation, portal_search, portal_room (uses {@code portal_room} metadata
 * {@code "x,y,z"} when the version provides it). Eye count via
 * {@code stronghold.eyes} when no loadout is configured.
 *
 * <p>Completion: {@code navigation} and {@code portal_search} finish on
 * reaching the portal room (stairs fallback when the version has no
 * {@code portal_room} metadata), {@code portal_room} on entering the End
 * portal, {@code entry} via {@code stronghold.goal} ({@code manual} by
 * default, {@code reach_stronghold} to finish near the stairs).
 */
public class StrongholdScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("stronghold");

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.STRONGHOLD;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.OVERWORLD);
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        String mode = context.settings().getOrDefault("stronghold.mode", "entry").trim().toLowerCase();
        int radius = context.settings().getInt("stronghold.searchRadius", 100000);
        Optional<StructureAdapter.StructureLocation> found = locate(context, "stronghold", spawnOf(context), radius);
        if (!found.isPresent()) {
            throw new PracticeException("No stronghold in range on seed " + context.seed(),
                    "Unable to start Stronghold Practice: no stronghold was found on this seed. Try a new seed.");
        }
        track(context, "target", found.get().position());
        String portalRoomMeta = found.get().metadata()
                .get(StructureAdapter.StructureLocation.PORTAL_ROOM_KEY);
        if (portalRoomMeta != null) {
            track(context, "portalRoom", portalRoomMeta);
        }
        PracticePosition target = found.get().position();
        if ("portal_room".equals(mode)) {
            PracticePosition portalRoom = parsePortalRoom(found.get().metadata()
                    .get(StructureAdapter.StructureLocation.PORTAL_ROOM_KEY));
            if (portalRoom != null) {
                target = portalRoom;
            } else {
                SpeedrunLogger.warn("Portal-room position unavailable; starting at the stronghold stairs");
            }
        } else if (!"entry".equals(mode) && !"navigation".equals(mode) && !"portal_search".equals(mode)) {
            SpeedrunLogger.warn("Unknown stronghold.mode \"" + mode + "\"; starting at the entry");
        }
        teleportStart(context, target);
        if (context.settings().loadoutId() == null && context.settings().get("stronghold.eyes") != null) {
            int eyes = Math.max(0, Math.min(64, context.settings().getInt("stronghold.eyes", 12)));
            List<Loadout.Item> items = new ArrayList<Loadout.Item>();
            items.add(new Loadout.Item("minecraft:ender_eye", 5, eyes));
            applyCompatibleLoadout(context, new Loadout("stronghold_eyes", items));
        } else {
            applyLoadoutSetting(context);
        }
    }

    private PracticePosition parsePortalRoom(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new PracticePosition(Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()), 90.0f, 0.0f);
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    @Override
    public TickResult tick(PracticeContext context) {
        String mode = context.settings().getOrDefault("stronghold.mode", "entry").trim().toLowerCase();
        PracticePosition stairs = tracked(context, "target");
        if (stairs == null) {
            return TickResult.continueTick();
        }
        if ("navigation".equals(mode) || "portal_search".equals(mode)) {
            double radius = Math.max(0, context.settings().getInt("stronghold.completeRadius", 8));
            PracticePosition portalRoom = parsePortalRoom(tracked(context, "portalRoom"));
            if (reached(context, portalRoom == null ? stairs : portalRoom, radius)) {
                return TickResult.finished();
            }
        } else if ("portal_room".equals(mode)) {
            if (currentDimension(context) == PracticeDimension.END) {
                return TickResult.finished();
            }
        } else if ("entry".equals(mode)) {
            String goal = context.settings().getOrDefault("stronghold.goal", "manual").trim().toLowerCase();
            if ("reach_stronghold".equals(goal)) {
                double radius = Math.max(0, context.settings().getInt("stronghold.completeRadius", 8));
                if (reached(context, stairs, radius)) {
                    return TickResult.finished();
                }
            } else if (!"manual".equals(goal)) {
                warnOnce(context, "goal", "Unknown stronghold.goal \"" + goal + "\"; never auto-finishing");
            }
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
