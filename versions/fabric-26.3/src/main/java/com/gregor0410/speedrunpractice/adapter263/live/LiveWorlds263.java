package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.adapter263.IPracticeServer263;
import com.gregor0410.speedrunpractice.adapter263.world.PracticeLevel263;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Practice-world lifecycle on the 26.3 server mixin: overworld/nether
 * practices share one linked triple (portal linkage and Nether entry need
 * the sibling worlds to exist); end practices get a single end world.
 * Every level is really generated from the requested practice seed — the
 * handle seed and the worldgen seed always match. Creation prunes the
 * previous set via the mixin, and deletion removes the whole triple.
 */
final class LiveWorlds263 implements WorldAdapter {
    private final LiveAdapter263 adapter;

    LiveWorlds263(LiveAdapter263 adapter) {
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
                PracticeLevel263 end = practiceServer(server).createEndPracticeWorld(seed);
                LiveWorld263 handle = new LiveWorld263(end, seed, PracticeDimension.END);
                adapter.track(handle);
                adapter.setCurrentWorld(handle);
                return handle;
            }
            Map<PracticeDimension, PracticeLevel263> linked =
                    practiceServer(server).createLinkedPracticeWorlds(seed);
            Map<PracticeDimension, LiveWorld263> triple =
                    new EnumMap<PracticeDimension, LiveWorld263>(PracticeDimension.class);
            triple.put(PracticeDimension.OVERWORLD, new LiveWorld263(
                    linked.get(PracticeDimension.OVERWORLD), seed, PracticeDimension.OVERWORLD));
            triple.put(PracticeDimension.NETHER, new LiveWorld263(
                    linked.get(PracticeDimension.NETHER), seed, PracticeDimension.NETHER));
            triple.put(PracticeDimension.END, new LiveWorld263(
                    linked.get(PracticeDimension.END), seed, PracticeDimension.END));
            adapter.trackTriple(triple);
            LiveWorld263 handle = triple.get(options.dimension());
            if (handle == null || handle.level() == null) {
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
        if (!(world instanceof LiveWorld263)) {
            throw new PracticeException("deletePracticeWorld got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            return;
        }
        LiveWorld263 handle = (LiveWorld263) world;
        if (adapter.currentWorld() == handle) {
            adapter.setCurrentWorld(null);
        }
        ResourceKey<Level> key = handle.level().dimension();
        Map<PracticeDimension, LiveWorld263> triple = adapter.forget(key);
        try {
            if (triple != null) {
                for (LiveWorld263 member : triple.values()) {
                    practiceServer(server).deletePracticeLevel(member.level());
                }
            } else {
                practiceServer(server).deletePracticeLevel(handle.level());
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
        if (!(world instanceof LiveWorld263)) {
            throw new PracticeException("resetPracticeWorld got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        if (options == null || options.dimension() == null) {
            throw new IllegalArgumentException("world options and dimension must not be null");
        }
        // No recycled-world path on 26.3 (FAST_WORLD_RESET stays false):
        // rebuild, then rebind the surviving handle so contexts keep working.
        LiveWorld263 handle = (LiveWorld263) world;
        ResourceKey<Level> staleKey = handle.level().dimension();
        Map<PracticeDimension, LiveWorld263> staleTriple = adapter.triple(staleKey);
        PracticeWorld fresh = createPracticeWorld(seed, options);
        ServerLevel freshLevel = ((LiveWorld263) fresh).level();
        handle.rebind(freshLevel, seed, options.dimension());
        // The mixin already pruned the stale set server-side; drop its
        // tracking and delete leftovers (a no-op when already pruned).
        MinecraftServer server = adapter.server();
        if (staleTriple != null) {
            adapter.forget(staleKey);
            if (server != null) {
                for (LiveWorld263 stale : staleTriple.values()) {
                    practiceServer(server).deletePracticeLevel(stale.level());
                }
            }
            // Point the fresh triple at the surviving handle.
            Map<PracticeDimension, LiveWorld263> freshTriple = adapter.triple(freshLevel.dimension());
            if (freshTriple != null) {
                Map<PracticeDimension, LiveWorld263> fixed =
                        new EnumMap<PracticeDimension, LiveWorld263>(PracticeDimension.class);
                fixed.putAll(freshTriple);
                fixed.put(handle.dimension(), handle);
                adapter.trackTriple(fixed);
            }
        } else {
            adapter.forget(staleKey);
            if (server != null) {
                practiceServer(server).deletePracticeLevel(server.getLevel(staleKey));
            }
            adapter.track(handle);
        }
        adapter.setCurrentWorld(handle);
    }

    @Override
    public PracticePosition spawnPosition(PracticeWorld world) throws PracticeException {
        LiveWorld263 handle = requireHandle(world, "spawnPosition");
        ServerLevel backing = LiveAdapter263.requireLevel(adapter, world, "spawnPosition");
        if (handle.dimension() == PracticeDimension.END) {
            BlockPos end = ServerLevel.END_SPAWN_POINT;
            return new PracticePosition(end.getX(), end.getY(), end.getZ(), 90.0f, 0.0f);
        }
        if (handle.dimension() == PracticeDimension.NETHER) {
            BlockPos overworldSpawn = siblingOverworldSpawn(handle, backing);
            return new PracticePosition(overworldSpawn.getX() / 8.0, overworldSpawn.getY(),
                    overworldSpawn.getZ() / 8.0, 90.0f, 0.0f);
        }
        BlockPos spawn = backing.getRespawnData().pos();
        return new PracticePosition(spawn.getX(), spawn.getY(), spawn.getZ(), 90.0f, 0.0f);
    }

    private BlockPos siblingOverworldSpawn(LiveWorld263 handle, ServerLevel backing) {
        MinecraftServer server = adapter.server();
        Map<PracticeDimension, LiveWorld263> triple = adapter.triple(backing.dimension());
        if (triple != null && triple.get(PracticeDimension.OVERWORLD) != null
                && server != null && server.getLevel(
                        triple.get(PracticeDimension.OVERWORLD).level().dimension()) != null) {
            return triple.get(PracticeDimension.OVERWORLD).level().getRespawnData().pos();
        }
        if (server != null && handle.level() instanceof PracticeLevel263) {
            ServerLevel sibling =
                    ((PracticeLevel263) handle.level()).associatedLevel(Level.OVERWORLD);
            if (sibling != null) {
                return sibling.getRespawnData().pos();
            }
        }
        return backing.getRespawnData().pos();
    }

    private static IPracticeServer263 practiceServer(MinecraftServer server) throws PracticeException {
        if (!(server instanceof IPracticeServer263)) {
            throw new PracticeException("Practice worlds need the 26.3 server mixin",
                    "Practice worlds are unavailable: the mod's server mixin did not apply.");
        }
        return (IPracticeServer263) server;
    }

    private static LiveWorld263 requireHandle(PracticeWorld world, String operation) throws PracticeException {
        if (!(world instanceof LiveWorld263)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        return (LiveWorld263) world;
    }
}
