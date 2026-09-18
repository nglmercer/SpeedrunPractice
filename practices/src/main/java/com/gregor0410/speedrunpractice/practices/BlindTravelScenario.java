package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Blind Travel practice (new). Seeded-random nether point at
 * {@code blind.targetDistance}; portal materials unless a loadout is set or
 * {@code blind.givePortalMats} is false.
 *
 * <p>Completion: the player builds and uses a portal; leaving the Nether
 * for the Overworld finishes the attempt and records the exit coordinates,
 * the nearest stronghold and the distance error on the context
 * ({@code blind.exit}, {@code blind.stronghold}, {@code blind.distance}).
 */
public class BlindTravelScenario extends AbstractPracticeScenario {
    public static final PracticeId ID = PracticeId.of("blind_travel");
    /** Context attribute: overworld exit as {@code "x,y,z"} once finished. */
    public static final String BLIND_EXIT_ATTRIBUTE = "blind.exit";
    /** Context attribute: nearest stronghold as {@code "x,y,z"} once finished. */
    public static final String BLIND_STRONGHOLD_ATTRIBUTE = "blind.stronghold";
    /** Context attribute: horizontal exit-to-stronghold distance once finished. */
    public static final String BLIND_DISTANCE_ATTRIBUTE = "blind.distance";

    @Override
    public PracticeId id() {
        return ID;
    }

    @Override
    public PracticeType type() {
        return PracticeType.BLIND_TRAVEL;
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        createWorld(context, PracticeDimension.NETHER);
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        PracticePosition spawn = spawnOf(context);
        int distance = Math.max(0, context.settings().getInt("blind.targetDistance", 1000));
        Random random = new Random(context.seed());
        double angle = random.nextDouble() * Math.PI * 2.0;
        PracticePosition target = new PracticePosition(
                spawn.x() + Math.cos(angle) * distance, spawn.y(), spawn.z() + Math.sin(angle) * distance, 90.0f, 0.0f);
        teleportStart(context, target);
        if (context.settings().loadoutId() == null && context.settings().getBoolean("blind.givePortalMats", true)) {
            List<Loadout.Item> items = new ArrayList<Loadout.Item>();
            items.add(new Loadout.Item("minecraft:obsidian", 0, 10));
            items.add(new Loadout.Item("minecraft:flint_and_steel", 1, 1));
            items.add(new Loadout.Item("minecraft:bread", 2, 16));
            applyCompatibleLoadout(context, new Loadout("blind_travel_mats", items));
        } else {
            applyLoadoutSetting(context);
        }
    }

    @Override
    public TickResult tick(PracticeContext context) {
        if (currentDimension(context) == PracticeDimension.OVERWORLD) {
            return finishWithMeasurement(context, null);
        }
        return TickResult.continueTick();
    }

    @Override
    public TickResult onEvent(PracticeContext context, PracticeEvent event) {
        if (event instanceof PracticeEvent.DimensionChangedEvent
                && ((PracticeEvent.DimensionChangedEvent) event).to() == PracticeDimension.OVERWORLD) {
            return finishWithMeasurement(context, null);
        }
        if (event instanceof PracticeEvent.PortalExitEvent
                && ((PracticeEvent.PortalExitEvent) event).entered() == PracticeDimension.OVERWORLD) {
            return finishWithMeasurement(context, ((PracticeEvent.PortalExitEvent) event).position());
        }
        return TickResult.continueTick();
    }

    private TickResult finishWithMeasurement(PracticeContext context, PracticePosition exit) {
        PracticePosition exitPosition = exit;
        if (exitPosition == null) {
            try {
                exitPosition = context.adapter().players().getPosition(context.player());
            } catch (PracticeException failure) {
                return TickResult.continueTick();
            } catch (RuntimeException failure) {
                return TickResult.continueTick();
            }
        }
        context.setAttribute(BLIND_EXIT_ATTRIBUTE, exitPosition.blockX() + "," + exitPosition.blockY() + ","
                + exitPosition.blockZ());
        try {
            Optional<StructureAdapter.StructureLocation> stronghold =
                    locate(context, "stronghold", exitPosition, 100000);
            if (stronghold.isPresent()) {
                PracticePosition target = stronghold.get().position();
                context.setAttribute(BLIND_STRONGHOLD_ATTRIBUTE,
                        target.blockX() + "," + target.blockY() + "," + target.blockZ());
                double dx = exitPosition.x() - target.x();
                double dz = exitPosition.z() - target.z();
                context.setAttribute(BLIND_DISTANCE_ATTRIBUTE, String.valueOf(Math.sqrt(dx * dx + dz * dz)));
            } else {
                context.setAttribute(BLIND_DISTANCE_ATTRIBUTE, "-1");
            }
        } catch (PracticeException failure) {
            context.setAttribute(BLIND_DISTANCE_ATTRIBUTE, "-1");
        } catch (RuntimeException failure) {
            context.setAttribute(BLIND_DISTANCE_ATTRIBUTE, "-1");
        }
        return TickResult.finished();
    }

    @Override
    public void stop(PracticeContext context) {
        deleteWorldQuietly(context);
    }
}
