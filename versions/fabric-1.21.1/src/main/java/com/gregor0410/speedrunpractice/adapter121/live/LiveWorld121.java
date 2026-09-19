package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.server.world.ServerWorld;

/**
 * Live 1.21.1 world handle. Mutable by design: {@code resetPracticeWorld}
 * rebinds the backing {@link ServerWorld} in place while the context keeps
 * holding this handle, so the handle id stays fixed for its lifetime (event
 * correlation) and {@link #seed()} tracks the current practice seed.
 */
public final class LiveWorld121 implements PracticeWorld {
    private final String handleId;
    private ServerWorld world;
    private long seed;
    private PracticeDimension dimension;

    public LiveWorld121(ServerWorld world, long seed, PracticeDimension dimension) {
        if (world == null || dimension == null) {
            throw new IllegalArgumentException("world and dimension must not be null");
        }
        this.world = world;
        this.seed = seed;
        this.dimension = dimension;
        this.handleId = world.getRegistryKey().getValue().toString();
    }

    public synchronized ServerWorld world() {
        return world;
    }

    public synchronized void rebind(ServerWorld world, long seed, PracticeDimension dimension) {
        if (world == null || dimension == null) {
            throw new IllegalArgumentException("world and dimension must not be null");
        }
        this.world = world;
        this.seed = seed;
        this.dimension = dimension;
    }

    @Override
    public String handleId() {
        return handleId;
    }

    @Override
    public synchronized long seed() {
        return seed;
    }

    @Override
    public synchronized PracticeDimension dimension() {
        return dimension;
    }

    @Override
    public GameVersion version() {
        return GameVersion.MC_1_21_1;
    }
}
