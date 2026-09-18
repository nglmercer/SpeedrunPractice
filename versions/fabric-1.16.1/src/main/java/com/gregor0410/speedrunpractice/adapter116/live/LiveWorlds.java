package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.IMinecraftServer;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionType;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Practice-world lifecycle on the legacy world machinery: overworld/nether
 * practices share one linked triple (portal linkage and Nether entry need
 * the sibling worlds to exist, exactly like the legacy commands); end
 * practices get a single end world. Creation prunes the previous set via the
 * legacy mixin, and deletion removes the whole triple.
 */
final class LiveWorlds implements WorldAdapter {
    private final LiveAdapter116 adapter;

    LiveWorlds(LiveAdapter116 adapter) {
        this.adapter = adapter;
    }

    @Override
    public PracticeWorld createPracticeWorld(long seed, PracticeWorldOptions options) throws PracticeException {
        if (options == null || options.dimension() == null) {
            throw new IllegalArgumentException("world options and dimension must not be null");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            throw new PracticeException("createPracticeWorld with no running server",
                    "Cannot start practice: no world is loaded. Open a single-player world first.");
        }
        try {
            if (options.dimension() == PracticeDimension.END) {
                ServerWorld end = ((IMinecraftServer) server).createEndPracticeWorld(seed);
                LiveWorld handle = new LiveWorld(end, seed, PracticeDimension.END);
                adapter.track(handle);
                adapter.setCurrentWorld(handle);
                return handle;
            }
            Map<RegistryKey<DimensionType>, com.gregor0410.speedrunpractice.world.PracticeWorld> linked =
                    ((IMinecraftServer) server).createLinkedPracticeWorld(seed);
            Map<PracticeDimension, LiveWorld> triple = new EnumMap<PracticeDimension, LiveWorld>(PracticeDimension.class);
            triple.put(PracticeDimension.OVERWORLD, new LiveWorld(
                    linked.get(DimensionType.OVERWORLD_REGISTRY_KEY), seed, PracticeDimension.OVERWORLD));
            triple.put(PracticeDimension.NETHER, new LiveWorld(
                    linked.get(DimensionType.THE_NETHER_REGISTRY_KEY), seed, PracticeDimension.NETHER));
            triple.put(PracticeDimension.END, new LiveWorld(
                    linked.get(DimensionType.THE_END_REGISTRY_KEY), seed, PracticeDimension.END));
            adapter.trackTriple(triple);
            // Legacy parity: keep the practice overworld spawn chunks loaded.
            com.gregor0410.speedrunpractice.world.PracticeWorld overworld =
                    linked.get(DimensionType.OVERWORLD_REGISTRY_KEY);
            overworld.getChunkManager().addTicket(ChunkTicketType.START,
                    new ChunkPos(overworld.getSpawnPos()), 11, Unit.INSTANCE);
            LiveWorld handle = triple.get(options.dimension());
            if (handle == null) {
                throw new PracticeException("createPracticeWorld lost dimension " + options.dimension(),
                        "Could not start practice: the world failed to build. Try again.");
            }
            adapter.setCurrentWorld(handle);
            return handle;
        } catch (IOException failure) {
            throw new PracticeException("createPracticeWorld failed: " + failure.getMessage(),
                    "Could not create the practice world: " + failure.getMessage());
        }
    }

    @Override
    public void deletePracticeWorld(PracticeWorld world) throws PracticeException {
        if (!(world instanceof LiveWorld)) {
            throw new PracticeException("deletePracticeWorld got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            return;
        }
        LiveWorld handle = (LiveWorld) world;
        if (adapter.currentWorld() == handle) {
            adapter.setCurrentWorld(null);
        }
        RegistryKey<?> key = handle.world().getRegistryKey();
        @SuppressWarnings("unchecked")
        Map<PracticeDimension, LiveWorld> triple =
                adapter.forget((RegistryKey<net.minecraft.world.World>) key);
        try {
            if (triple != null) {
                for (LiveWorld member : triple.values()) {
                    ((IMinecraftServer) server).deletePracticeWorld(member.world());
                }
            } else {
                ((IMinecraftServer) server).deletePracticeWorld(handle.world());
            }
        } catch (IllegalStateException failure) {
            throw new PracticeException("deletePracticeWorld failed: " + failure.getMessage(),
                    "Could not delete the practice world: " + failure.getMessage());
        } catch (RuntimeException failure) {
            throw new PracticeException("deletePracticeWorld failed: " + failure,
                    "Could not delete the practice world. It will be pruned on the next start.");
        }
    }

    @Override
    public void resetPracticeWorld(PracticeWorld world, long seed, PracticeWorldOptions options)
            throws PracticeException {
        if (!(world instanceof LiveWorld)) {
            throw new PracticeException("resetPracticeWorld got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        if (options == null || options.dimension() == null) {
            throw new IllegalArgumentException("world options and dimension must not be null");
        }
        // No recycled-world path on 1.16.1 (FAST_WORLD_RESET stays false):
        // rebuild, then rebind the surviving handle so contexts keep working.
        LiveWorld handle = (LiveWorld) world;
        RegistryKey<net.minecraft.world.World> staleKey = handle.world().getRegistryKey();
        Map<PracticeDimension, LiveWorld> staleTriple = adapter.triple(staleKey);
        PracticeWorld fresh = createPracticeWorld(seed, options);
        ServerWorld freshWorld = ((LiveWorld) fresh).world();
        handle.rebind(freshWorld, seed);
        // The legacy mixin already pruned the stale set server-side; drop its
        // tracking and delete leftovers (a no-op when already pruned).
        MinecraftServer server = adapter.server();
        if (staleTriple != null) {
            adapter.forget(staleKey);
            if (server != null) {
                for (LiveWorld stale : staleTriple.values()) {
                    ((IMinecraftServer) server).deletePracticeWorld(stale.world());
                }
            }
            // Point the fresh triple at the surviving handle.
            Map<PracticeDimension, LiveWorld> freshTriple = adapter.triple(freshWorld.getRegistryKey());
            if (freshTriple != null) {
                Map<PracticeDimension, LiveWorld> fixed =
                        new EnumMap<PracticeDimension, LiveWorld>(PracticeDimension.class);
                fixed.putAll(freshTriple);
                fixed.put(handle.dimension(), handle);
                adapter.trackTriple(fixed);
            }
        } else {
            adapter.forget(staleKey);
            if (server != null) {
                ((IMinecraftServer) server).deletePracticeWorld(server.getWorld(staleKey));
            }
            adapter.track(handle);
        }
        adapter.setCurrentWorld(handle);
    }

    @Override
    public PracticePosition spawnPosition(PracticeWorld world) throws PracticeException {
        LiveWorld handle = requireHandle(world, "spawnPosition");
        ServerWorld backing = LiveAdapter116.requireWorld(adapter, world, "spawnPosition");
        if (handle.dimension() == PracticeDimension.END) {
            // Legacy parity: the obsidian platform raised by resetFight.
            return new PracticePosition(100.0, 49.0, 0.0, 90.0f, 0.0f);
        }
        if (handle.dimension() == PracticeDimension.NETHER) {
            BlockPos overworldSpawn = siblingOverworldSpawn(handle, backing);
            return new PracticePosition(overworldSpawn.getX() / 8.0, overworldSpawn.getY(),
                    overworldSpawn.getZ() / 8.0, 90.0f, 0.0f);
        }
        BlockPos spawn = backing.getSpawnPos();
        return new PracticePosition(spawn.getX(), spawn.getY(), spawn.getZ(), 90.0f, 0.0f);
    }

    private BlockPos siblingOverworldSpawn(LiveWorld handle, ServerWorld backing) {
        MinecraftServer server = adapter.server();
        Map<PracticeDimension, LiveWorld> triple = adapter.triple(backing.getRegistryKey());
        if (triple != null && triple.get(PracticeDimension.OVERWORLD) != null
                && server != null && server.getWorld(
                        triple.get(PracticeDimension.OVERWORLD).world().getRegistryKey()) != null) {
            return triple.get(PracticeDimension.OVERWORLD).world().getSpawnPos();
        }
        if (server != null) {
            return server.getOverworld().getSpawnPos();
        }
        return backing.getSpawnPos();
    }

    private static LiveWorld requireHandle(PracticeWorld world, String operation) throws PracticeException {
        if (!(world instanceof LiveWorld)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        return (LiveWorld) world;
    }
}
