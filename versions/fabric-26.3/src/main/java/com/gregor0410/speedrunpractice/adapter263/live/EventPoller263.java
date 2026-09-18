package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.HashSet;
import java.util.Set;

/**
 * Translates live 26.3 game state into shared {@link PracticeEvent}s once
 * per server tick: movement, dimension changes, Nether portal exits,
 * structure entries, dragon kills and inventory changes all feed
 * {@code engine.fireEvent}, which drives timer start/stop conditions and
 * scenario completion. Resets and fresh starts re-seed the baseline
 * silently, so the start teleport never fakes a dimension entry.
 *
 * <p>Manual timer events have no source until keybinds land. Structure and
 * dragon checks run every 10 ticks; movement, dimension and inventory run
 * every tick. Nothing here ever throws out of the tick. Dragon events stay
 * silent until the dragon slice lands (its pending failure is caught, like
 * any other unavailable check).
 */
final class EventPoller263 {
    private static final String[] POLLED_STRUCTURES = new String[]{
            "stronghold", "bastion_remnant", "fortress", "village"};
    private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private final LiveAdapter263 adapter;
    private PracticeSession session;
    private long seed;
    private String worldHandle;
    private BlockPos lastBlock;
    private PracticePosition lastPrecise;
    private PracticeDimension lastDim;
    private ResourceKey<Level> lastLevelKey;
    private int lastInvSig;
    private boolean lastDragonAlive;
    private final Set<String> insideStructures = new HashSet<String>();
    private int tick;

    EventPoller263(LiveAdapter263 adapter) {
        this.adapter = adapter;
    }

    void poll(PracticeRuntime runtime) {
        if (runtime == null || !runtime.hasActive()) {
            return;
        }
        tick++;
        PracticeContext context;
        try {
            context = runtime.engine().currentContext();
        } catch (RuntimeException bad) {
            return;
        }
        if (context == null || context.session() == null
                || context.session().state() != PracticeState.RUNNING
                || !(context.player() instanceof LivePlayer263)) {
            return;
        }
        ServerPlayer player = ((LivePlayer263) context.player()).entity();
        if (context.world() == null) {
            return;
        }
        String handleId = context.world().handleId();
        if (session != context.session() || seed != context.seed() || !handleId.equals(worldHandle)) {
            baseline(context, player);
            return;
        }
        pollMovement(runtime, player);
        pollDimension(runtime, player);
        pollInventory(runtime, player);
        if (tick % 10 == 0) {
            pollDragon(runtime, context);
            pollStructures(runtime, player);
        }
    }

    private void baseline(PracticeContext context, ServerPlayer player) {
        session = context.session();
        seed = context.seed();
        worldHandle = context.world().handleId();
        lastBlock = player.blockPosition();
        lastPrecise = precise(player);
        lastLevelKey = player.level().dimension();
        lastDim = dimensionOf(player.level());
        lastInvSig = inventorySignature(player);
        insideStructures.clear();
        try {
            lastDragonAlive = adapter.dragons().hasLivingDragon(context.world());
        } catch (PracticeException bad) {
            lastDragonAlive = false;
        } catch (RuntimeException bad) {
            lastDragonAlive = false;
        }
    }

    private void pollMovement(PracticeRuntime runtime, ServerPlayer player) {
        BlockPos block = player.blockPosition();
        PracticePosition precise = precise(player);
        if (!block.equals(lastBlock)) {
            fire(runtime, new PracticeEvent.PlayerMovedEvent(lastPrecise, precise));
            lastBlock = block;
        }
        lastPrecise = precise;
    }

    private void pollDimension(PracticeRuntime runtime, ServerPlayer player) {
        ResourceKey<Level> key = player.level().dimension();
        if (key.equals(lastLevelKey)) {
            return;
        }
        PracticeDimension from = lastDim;
        PracticeDimension to = dimensionOf(player.level());
        lastLevelKey = key;
        lastDim = to;
        fire(runtime, new PracticeEvent.DimensionChangedEvent(from, to));
        if (isNetherPortalTrip(from, to)) {
            fire(runtime, new PracticeEvent.PortalExitEvent(to, precise(player)));
        }
    }

    private void pollInventory(PracticeRuntime runtime, ServerPlayer player) {
        int signature = inventorySignature(player);
        if (signature != lastInvSig) {
            lastInvSig = signature;
            fire(runtime, new PracticeEvent.InventoryChangedEvent());
        }
    }

    private void pollDragon(PracticeRuntime runtime, PracticeContext context) {
        if (context.world().dimension() != PracticeDimension.END) {
            return;
        }
        boolean alive;
        try {
            alive = adapter.dragons().hasLivingDragon(context.world());
        } catch (PracticeException bad) {
            return;
        } catch (RuntimeException bad) {
            return;
        }
        if (lastDragonAlive && !alive) {
            fire(runtime, new PracticeEvent.DragonKilledEvent(context.world().handleId()));
        }
        lastDragonAlive = alive;
    }

    private void pollStructures(PracticeRuntime runtime, ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        for (String id : POLLED_STRUCTURES) {
            boolean inside = false;
            try {
                inside = isInside(level, id, pos);
            } catch (RuntimeException bad) {
                continue;
            }
            if (inside && insideStructures.add(id)) {
                fire(runtime, new PracticeEvent.StructureEnteredEvent(id,
                        new PracticePosition(pos.getX(), pos.getY(), pos.getZ())));
            } else if (!inside) {
                insideStructures.remove(id);
            }
        }
    }

    private static boolean isInside(ServerLevel level, String id, BlockPos pos) {
        try {
            StructureStart start =
                    level.structureManager().getStructureWithPieceAt(pos, FeatureIds263.tagKey(id));
            return start != null && start.isValid();
        } catch (PracticeException bad) {
            return false;
        }
    }

    private PracticeDimension dimensionOf(ServerLevel level) {
        LiveWorld263 tracked = adapter.tracked(level.dimension());
        if (tracked != null) {
            return tracked.dimension();
        }
        return LivePlayers263.dimensionOf(level);
    }

    private static boolean isNetherPortalTrip(PracticeDimension from, PracticeDimension to) {
        return (from == PracticeDimension.OVERWORLD && to == PracticeDimension.NETHER)
                || (from == PracticeDimension.NETHER && to == PracticeDimension.OVERWORLD);
    }

    private static PracticePosition precise(ServerPlayer player) {
        return new PracticePosition(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    private static int inventorySignature(ServerPlayer player) {
        int signature = player.getInventory().getSelectedSlot();
        signature = mixInventory(signature, player.getInventory().getNonEquipmentItems());
        for (EquipmentSlot slot : ARMOR) {
            signature = mixStack(signature, player.getItemBySlot(slot));
        }
        signature = mixStack(signature, player.getItemBySlot(EquipmentSlot.OFFHAND));
        return signature;
    }

    private static int mixInventory(int signature, NonNullList<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            signature = mixStack(signature, stacks.get(i));
        }
        return signature;
    }

    private static int mixStack(int signature, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            signature = signature * 31 + BuiltInRegistries.ITEM.getId(stack.getItem());
            signature = signature * 31 + stack.getCount();
        }
        return signature;
    }

    private static void fire(PracticeRuntime runtime, PracticeEvent event) {
        try {
            runtime.engine().fireEvent(event);
        } catch (PracticeException bad) {
            SpeedrunLogger.warn("Practice event failed: " + bad.getUserMessage());
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Practice event failed: " + bad);
        }
    }
}
