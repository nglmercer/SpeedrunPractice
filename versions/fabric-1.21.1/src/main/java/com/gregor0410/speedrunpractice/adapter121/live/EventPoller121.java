package com.gregor0410.speedrunpractice.adapter121.live;

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
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Translates live 1.21.1 game state into shared {@link PracticeEvent}s once
 * per server tick (plan step 05): movement, dimension changes, Nether portal
 * exits, structure entries, dragon kills and inventory changes all feed
 * {@code engine.fireEvent}, which drives timer start/stop conditions and
 * scenario completion. Resets and fresh starts re-seed the baseline
 * silently, so the start teleport never fakes a dimension entry.
 *
 * <p>Manual timer events have no source until keybinds land. Structure and
 * dragon checks run every 10 ticks; movement, dimension and inventory run
 * every tick. Nothing here ever throws out of the tick.
 */
final class EventPoller121 {
    private static final String[] POLLED_STRUCTURES = new String[]{
            "stronghold", "bastion_remnant", "fortress", "village"};

    private final LiveAdapter121 adapter;
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

    EventPoller121(LiveAdapter121 adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
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
                || !(context.player() instanceof LivePlayer121)) {
            return;
        }
        ServerPlayerEntity player = ((LivePlayer121) context.player()).entity();
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

    private void pollDimension(PracticeRuntime runtime, ServerPlayerEntity player) {
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
        for (String id : POLLED_STRUCTURES) {
            boolean inside = false;
            try {
                inside = isInside(world, id, pos);
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

    /**
     * True when the position sits inside a generated piece of the structure:
     * resolves the id through the structure tag first (family ids like
     * {@code village}), then asks the accessor which start contains the
     * position. The accessor resolves the chunk's structure references, so
     * pieces are found even when the start was recorded in a neighbouring
     * chunk (a raw chunk-starts scan misses those).
     */
    private boolean isInside(ServerWorld world, String id, BlockPos pos) {
        MinecraftServer server = adapter.server();
        if (server == null) {
            return false;
        }
        List<RegistryEntry<Structure>> candidates;
        try {
            candidates = resolveCandidates(server, id);
        } catch (PracticeException bad) {
            return false;
        }
        for (RegistryEntry<Structure> candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            StructureStart start = world.getStructureAccessor()
                    .getStructureContaining(pos, candidate.value());
            if (start != null && start != StructureStart.DEFAULT && start.hasChildren()) {
                return true;
            }
        }
        return false;
    }

    private static List<RegistryEntry<Structure>> resolveCandidates(MinecraftServer server,
            String id) throws PracticeException {
        Registry<Structure> structures =
                server.getRegistryManager().get(RegistryKeys.STRUCTURE);
        if (structures == null) {
            return Collections.emptyList();
        }
        try {
            Optional<RegistryEntryList.Named<Structure>> tag =
                    structures.getEntryList(FeatureIds121.tagKey(id));
            if (tag != null && tag.isPresent() && tag.get().size() > 0) {
                List<RegistryEntry<Structure>> members =
                        new ArrayList<RegistryEntry<Structure>>(tag.get().size());
                for (RegistryEntry<Structure> member : tag.get()) {
                    members.add(member);
                }
                return members;
            }
        } catch (RuntimeException bad) {
            return Collections.emptyList();
        }
        try {
            Optional<RegistryEntry.Reference<Structure>> direct =
                    structures.getEntry(FeatureIds121.key(id));
            if (direct != null && direct.isPresent()) {
                List<RegistryEntry<Structure>> single =
                        new ArrayList<RegistryEntry<Structure>>(1);
                single.add(direct.get());
                return single;
            }
        } catch (RuntimeException bad) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private PracticeDimension dimensionOf(ServerWorld world) {
        LiveWorld121 tracked = adapter.tracked(world.getRegistryKey());
        if (tracked != null) {
            return tracked.dimension();
        }
        return LivePlayers121.dimensionOf(world);
    }

    private static boolean isNetherPortalTrip(PracticeDimension from, PracticeDimension to) {
        return (from == PracticeDimension.OVERWORLD && to == PracticeDimension.NETHER)
                || (from == PracticeDimension.NETHER && to == PracticeDimension.OVERWORLD);
    }

    private static PracticePosition precise(ServerPlayerEntity player) {
        return new PracticePosition(player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch());
    }

    private static int inventorySignature(ServerPlayerEntity player) {
        int signature = player.getInventory().selectedSlot;
        signature = mixInventory(signature, player.getInventory().main);
        signature = mixInventory(signature, player.getInventory().armor);
        signature = mixInventory(signature, player.getInventory().offHand);
        return signature;
    }

    private static int mixInventory(int signature, DefaultedList<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                signature = signature * 31 + Registries.ITEM.getRawId(stack.getItem());
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
