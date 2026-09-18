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

/**
 * Stronghold practice (legacy parity). {@code stronghold.mode}: entry,
 * navigation, portal_search, portal_room (uses {@code portal_room} metadata
 * {@code "x,y,z"} when the version provides it). Eye count via
 * {@code stronghold.eyes} when no loadout is configured.
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
        PracticePosition target = found.get().position();
        if ("portal_room".equals(mode)) {
            PracticePosition portalRoom = parsePortalRoom(found.get().metadata().get("portal_room"));
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
            context.adapter().players().applyLoadout(context.player(), new Loadout("stronghold_eyes", items));
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
        return TickResult.continueTick();
    }

    @Override
    public void stop(PracticeContext context) {
        deleteWorldQuietly(context);
    }
}
