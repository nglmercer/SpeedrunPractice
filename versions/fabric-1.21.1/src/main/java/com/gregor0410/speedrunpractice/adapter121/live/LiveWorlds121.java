package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.adapter121.IPracticeServer121;
import com.gregor0410.speedrunpractice.adapter121.world.PracticeWorld121;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.level.LevelProperties;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Practice-world lifecycle on the 1.21.1 server mixin: overworld/nether
 * practices share one linked triple (portal linkage and Nether entry need
 * the sibling worlds to exist); end practices get a single end world. Every
 * world is really generated from the requested practice seed — the handle
 * seed and the worldgen seed always match. Creation prunes the previous set
 * via the mixin, and deletion removes the whole triple.
 */
final class LiveWorlds121 implements WorldAdapter {
    private final LiveAdapter121 adapter;

    LiveWorlds121(LiveAdapter121 adapter) {
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
                PracticeWorld121 end = practiceServer(server).createEndPracticeWorld(seed);
                LiveWorld121 handle = new LiveWorld121(end, seed, PracticeDimension.END);
                adapter.track(handle);
                adapter.setCurrentWorld(handle);
                return handle;
            }
            Map<PracticeDimension, PracticeWorld121> linked =
                    practiceServer(server).createLinkedPracticeWorlds(seed);
            Map<PracticeDimension, LiveWorld121> triple =
                    new EnumMap<PracticeDimension, LiveWorld121>(PracticeDimension.class);
            triple.put(PracticeDimension.OVERWORLD, new LiveWorld121(
                    linked.get(PracticeDimension.OVERWORLD), seed, PracticeDimension.OVERWORLD));
            triple.put(PracticeDimension.NETHER, new LiveWorld121(
                    linked.get(PracticeDimension.NETHER), seed, PracticeDimension.NETHER));
            triple.put(PracticeDimension.END, new LiveWorld121(
                    linked.get(PracticeDimension.END), seed, PracticeDimension.END));
            adapter.trackTriple(triple);
            LiveWorld121 handle = triple.get(options.dimension());
            if (handle == null || handle.world() == null) {
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
        if (!(world instanceof LiveWorld121)) {
            throw new PracticeException("deletePracticeWorld got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            return;
        }
        LiveWorld121 handle = (LiveWorld121) world;
        if (adapter.currentWorld() == handle) {
            adapter.setCurrentWorld(null);
        }
        RegistryKey<World> key = handle.world().getRegistryKey();
        Map<PracticeDimension, LiveWorld121> triple = adapter.forget(key);
        try {
            if (triple != null) {
                for (LiveWorld121 member : triple.values()) {
                    practiceServer(server).deletePracticeWorld(member.world());
                }
            } else {
                practiceServer(server).deletePracticeWorld(handle.world());
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
        if (!(world instanceof LiveWorld121)) {
            throw new PracticeException("resetPracticeWorld got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        if (options == null || options.dimension() == null) {
            throw new IllegalArgumentException("world options and dimension must not be null");
        }
        // No recycled-world path on 1.21.1 (FAST_WORLD_RESET stays false):
        // rebuild, then rebind the surviving handle so contexts keep working.
        LiveWorld121 handle = (LiveWorld121) world;
        RegistryKey<World> staleKey = handle.world().getRegistryKey();
        Map<PracticeDimension, LiveWorld121> staleTriple = adapter.triple(staleKey);
        PracticeWorld fresh = createPracticeWorld(seed, options);
        ServerWorld freshWorld = ((LiveWorld121) fresh).world();
        // Drop the stale triple BEFORE rebinding: the stale map aliases the
        // surviving handle object, so rebinding first would untrack the fresh
        // keys and delete the fresh world instead of the stale one.
        // The mixin already pruned the stale set server-side; this drops its
        // tracking and deletes leftovers (a no-op when already pruned).
        MinecraftServer server = adapter.server();
        if (staleTriple != null) {
            adapter.forget(staleKey);
            if (server != null) {
                for (LiveWorld121 stale : staleTriple.values()) {
                    practiceServer(server).deletePracticeWorld(stale.world());
                }
            }
        } else {
            adapter.forget(staleKey);
            if (server != null) {
                practiceServer(server).deletePracticeWorld(server.getWorld(staleKey));
            }
        }
        handle.rebind(freshWorld, seed, options.dimension());
        if (staleTriple != null) {
            // Point the fresh triple at the surviving handle.
            Map<PracticeDimension, LiveWorld121> freshTriple = adapter.triple(freshWorld.getRegistryKey());
            if (freshTriple != null) {
                Map<PracticeDimension, LiveWorld121> fixed =
                        new EnumMap<PracticeDimension, LiveWorld121>(PracticeDimension.class);
                fixed.putAll(freshTriple);
                fixed.put(handle.dimension(), handle);
                adapter.trackTriple(fixed);
            }
        } else {
            adapter.track(handle);
        }
        adapter.setCurrentWorld(handle);
    }

    @Override
    public PracticePosition spawnPosition(PracticeWorld world) throws PracticeException {
        LiveWorld121 handle = requireHandle(world, "spawnPosition");
        ServerWorld backing = LiveAdapter121.requireWorld(adapter, world, "spawnPosition");
        if (handle.dimension() == PracticeDimension.END) {
            BlockPos end = ServerWorld.END_SPAWN_POS;
            return new PracticePosition(end.getX(), end.getY(), end.getZ(), 90.0f, 0.0f);
        }
        if (handle.dimension() == PracticeDimension.NETHER) {
            BlockPos overworldSpawn = siblingOverworldSpawn(handle, backing);
            return new PracticePosition(overworldSpawn.getX() / 8.0, overworldSpawn.getY(),
                    overworldSpawn.getZ() / 8.0, 90.0f, 0.0f);
        }
        BlockPos spawn = spawnOf(backing);
        return new PracticePosition(spawn.getX(), spawn.getY(), spawn.getZ(), 90.0f, 0.0f);
    }

    private BlockPos siblingOverworldSpawn(LiveWorld121 handle, ServerWorld backing) {
        MinecraftServer server = adapter.server();
        Map<PracticeDimension, LiveWorld121> triple = adapter.triple(backing.getRegistryKey());
        if (triple != null && triple.get(PracticeDimension.OVERWORLD) != null
                && server != null && server.getWorld(
                        triple.get(PracticeDimension.OVERWORLD).world().getRegistryKey()) != null) {
            return spawnOf(triple.get(PracticeDimension.OVERWORLD).world());
        }
        if (handle.world() instanceof PracticeWorld121) {
            ServerWorld sibling =
                    ((PracticeWorld121) handle.world()).associatedLevel(World.OVERWORLD);
            if (sibling != null) {
                return spawnOf(sibling);
            }
        }
        return spawnOf(backing);
    }

    private static BlockPos spawnOf(ServerWorld world) {
        if (world.getLevelProperties() instanceof LevelProperties) {
            return ((LevelProperties) world.getLevelProperties()).getSpawnPos();
        }
        return new BlockPos(0, 64, 0);
    }

    private static IPracticeServer121 practiceServer(MinecraftServer server) throws PracticeException {
        if (!(server instanceof IPracticeServer121)) {
            throw new PracticeException("Practice worlds need the 1.21.1 server mixin",
                    "Practice worlds are unavailable: the mod's server mixin did not apply.");
        }
        return (IPracticeServer121) server;
    }

    private static LiveWorld121 requireHandle(PracticeWorld world, String operation) throws PracticeException {
        if (!(world instanceof LiveWorld121)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        return (LiveWorld121) world;
    }
}
