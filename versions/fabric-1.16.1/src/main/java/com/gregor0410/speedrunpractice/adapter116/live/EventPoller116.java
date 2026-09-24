package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Translates live 1.16.1 game state into shared {@link PracticeEvent}s once
 * per server tick (plan step 05): movement, dimension changes, Nether portal
 * exits, structure entries, dragon kills and inventory changes all feed
 * {@code engine.fireEvent}, which drives timer start/stop conditions and
 * scenario completion. Resets and fresh starts re-seed the baseline
 * silently, so the start teleport never fakes a dimension entry.
 *
 * <p>Manual timer events have no source until keybinds land (step 08).
 * Structure and dragon checks run every 10 ticks; movement, dimension and
 * inventory run every tick. Nothing here ever throws out of the tick.
 */
final class EventPoller116 {
    private static final Map<String, StructureFeature<?>> POLLED_STRUCTURES = polledStructures();

    private static Map<String, StructureFeature<?>> polledStructures() {
        Map<String, StructureFeature<?>> map = new HashMap<String, StructureFeature<?>>();
        map.put("stronghold", StructureFeature.STRONGHOLD);
        map.put("bastion_remnant", StructureFeature.BASTION_REMNANT);
        map.put("fortress", StructureFeature.FORTRESS);
        map.put("village", StructureFeature.VILLAGE);
        return map;
    }

    private final LiveAdapter116 adapter;
    private PracticeSession session;
    private long seed;
    private String worldHandle;
    private BlockPos lastBlock;
    private PracticePosition lastPrecise;
    private PracticeDimension lastDim;
    private RegistryKey<World> lastWorldKey;
    private int lastInvSig;
    private boolean lastDragonAlive;
    private final Set<String> insideStructures = new HashSet<String>();
    private int tick;

    EventPoller116(LiveAdapter116 adapter) {
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
                || !(context.player() instanceof LivePlayer)) {
            return;
        }
        ServerPlayerEntity player = ((LivePlayer) context.player()).entity();
        if (context.world() == null) {
            return;
        }
        String handleId = context.world().handleId();
        if (session != context.session() || seed != context.seed() || !handleId.equals(worldHandle)) {
            baseline(context, player);
            return;
        }
        pollMovement(runtime, player);
        pollDimension(runtime, context, player);
        pollInventory(runtime, player);
        if (tick % 10 == 0) {
            pollDragon(runtime, context);
            pollStructures(runtime, player);
        }
    }

    private void baseline(PracticeContext context, ServerPlayerEntity player) {
        session = context.session();
        seed = context.seed();
        worldHandle = context.world().handleId();
        lastBlock = player.getBlockPos();
        lastPrecise = precise(player);
        lastWorldKey = player.getServerWorld().getRegistryKey();
        lastDim = dimensionOf(player.getServerWorld());
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

    private void pollMovement(PracticeRuntime runtime, ServerPlayerEntity player) {
        BlockPos block = player.getBlockPos();
        PracticePosition precise = precise(player);
        if (!block.equals(lastBlock)) {
            fire(runtime, new PracticeEvent.PlayerMovedEvent(lastPrecise, precise));
            lastBlock = block;
        }
        lastPrecise = precise;
    }

    private void pollDimension(PracticeRuntime runtime, PracticeContext context, ServerPlayerEntity player) {
        RegistryKey<World> key = player.getServerWorld().getRegistryKey();
        if (key.equals(lastWorldKey)) {
            return;
        }
        PracticeDimension from = lastDim;
        PracticeDimension to = dimensionOf(player.getServerWorld());
        lastWorldKey = key;
        lastDim = to;
        fire(runtime, new PracticeEvent.DimensionChangedEvent(from, to));
        if (isNetherPortalTrip(from, to)) {
            fire(runtime, new PracticeEvent.PortalExitEvent(to, precise(player)));
        }
    }

    private void pollInventory(PracticeRuntime runtime, ServerPlayerEntity player) {
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

    private void pollStructures(PracticeRuntime runtime, ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos pos = player.getBlockPos();
        for (Map.Entry<String, StructureFeature<?>> entry : POLLED_STRUCTURES.entrySet()) {
            boolean inside = false;
            try {
                inside = isInside(world, entry.getValue(), pos);
            } catch (RuntimeException bad) {
                continue;
            }
            if (inside && insideStructures.add(entry.getKey())) {
                fire(runtime, new PracticeEvent.StructureEnteredEvent(entry.getKey(),
                        new PracticePosition(pos.getX(), pos.getY(), pos.getZ())));
            } else if (!inside) {
                insideStructures.remove(entry.getKey());
            }
        }
    }

    private static boolean isInside(ServerWorld world, StructureFeature<?> feature, BlockPos pos) {
        // Reference-resolving lookup: the start recorded for this chunk
        // column may live in a neighbouring chunk (multi-chunk villages),
        // which a holder-local scan misses.
        Chunk chunk = world.getChunk(pos);
        ChunkPos chunkPos = chunk.getPos();
        Object[] starts = world.getStructureAccessor()
                .getStructuresWithChildren(ChunkSectionPos.from(chunkPos, 0), feature).toArray();
        for (int s = 0; s < starts.length; s++) {
            StructureStart<?> start = (StructureStart<?>) starts[s];
            if (start == null) {
                continue;
            }
            if (start.getBoundingBox() != null && start.getBoundingBox().contains(pos)) {
                return true;
            }
            if (start.getChildren() != null) {
                for (int i = 0; i < start.getChildren().size(); i++) {
                    if (start.getChildren().get(i).getBoundingBox() != null
                            && start.getChildren().get(i).getBoundingBox().contains(pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private PracticeDimension dimensionOf(ServerWorld world) {
        LiveWorld tracked = adapter.tracked(world.getRegistryKey());
        if (tracked != null) {
            return tracked.dimension();
        }
        return LivePlayers.dimensionOf(world);
    }

    private static boolean isNetherPortalTrip(PracticeDimension from, PracticeDimension to) {
        return (from == PracticeDimension.OVERWORLD && to == PracticeDimension.NETHER)
                || (from == PracticeDimension.NETHER && to == PracticeDimension.OVERWORLD);
    }

    private static PracticePosition precise(ServerPlayerEntity player) {
        return new PracticePosition(player.getX(), player.getY(), player.getZ(), player.yaw, player.pitch);
    }

    private static int inventorySignature(ServerPlayerEntity player) {
        int signature = player.inventory.selectedSlot;
        signature = mixInventory(signature, player.inventory.main);
        signature = mixInventory(signature, player.inventory.armor);
        signature = mixInventory(signature, player.inventory.offHand);
        return signature;
    }

    private static int mixInventory(int signature,
            net.minecraft.util.collection.DefaultedList<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                signature = signature * 31 + Registry.ITEM.getRawId(stack.getItem());
                signature = signature * 31 + stack.getCount();
            }
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
