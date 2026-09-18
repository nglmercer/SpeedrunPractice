package com.gregor0410.speedrunpractice.adapter263.live;

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

/**
 * Practice-world lifecycle on the live vanilla levels: each dimension maps
 * to the server's own {@link ServerLevel} for that dimension. Creation and
 * reset only (re)bind handles — no world files are created, deleted or
 * reseeded — so deletion just drops tracking. Per-seed world recreation is
 * a follow-up; until then practices run in the live world at the requested
 * practice seed.
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
        ServerLevel level = levelFor(server, options.dimension());
        LiveWorld263 handle = new LiveWorld263(level, seed, options.dimension());
        adapter.track(handle);
        adapter.setCurrentWorld(handle);
        return handle;
    }

    @Override
    public void deletePracticeWorld(PracticeWorld world) throws PracticeException {
        LiveWorld263 handle = requireHandle(world, "deletePracticeWorld");
        if (adapter.currentWorld() == handle) {
            adapter.setCurrentWorld(null);
        }
        adapter.forget(handle.level().dimension());
    }

    @Override
    public void resetPracticeWorld(PracticeWorld world, long seed, PracticeWorldOptions options)
            throws PracticeException {
        LiveWorld263 handle = requireHandle(world, "resetPracticeWorld");
        if (options == null || options.dimension() == null) {
            throw new IllegalArgumentException("world options and dimension must not be null");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            throw new PracticeException("resetPracticeWorld with no running server",
                    "Cannot reset practice: no world is loaded. Open a single-player world first.");
        }
        ServerLevel level = levelFor(server, options.dimension());
        handle.rebind(level, seed, options.dimension());
        adapter.track(handle);
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
        MinecraftServer server = adapter.server();
        ServerLevel overworld = server == null ? null : server.getLevel(Level.OVERWORLD);
        BlockPos spawn = overworld == null ? backing.getRespawnData().pos()
                : overworld.getRespawnData().pos();
        if (handle.dimension() == PracticeDimension.NETHER) {
            return new PracticePosition(spawn.getX() / 8.0, spawn.getY(), spawn.getZ() / 8.0, 90.0f, 0.0f);
        }
        return new PracticePosition(spawn.getX(), spawn.getY(), spawn.getZ(), 90.0f, 0.0f);
    }

    private static ServerLevel levelFor(MinecraftServer server, PracticeDimension dimension)
            throws PracticeException {
        ResourceKey<Level> key;
        switch (dimension) {
            case NETHER:
                key = Level.NETHER;
                break;
            case END:
                key = Level.END;
                break;
            case OVERWORLD:
            default:
                key = Level.OVERWORLD;
                break;
        }
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            throw new PracticeException("No live level for dimension " + dimension,
                    "That dimension is not loaded. Stop and start the practice again.");
        }
        return level;
    }

    private static LiveWorld263 requireHandle(PracticeWorld world, String operation) throws PracticeException {
        if (!(world instanceof LiveWorld263)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        return (LiveWorld263) world;
    }
}
