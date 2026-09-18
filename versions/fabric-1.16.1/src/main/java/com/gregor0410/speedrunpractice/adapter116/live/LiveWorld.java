package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.server.world.ServerWorld;

/**
 * Live 1.16.1 world handle. Mutable by design: {@code resetPracticeWorld}
 * deletes the backing {@link ServerWorld} and creates a new one while the
 * context keeps holding this handle, so the handle updates in place. The
 * handle id stays fixed for the handle's lifetime (event correlation);
 * {@link #seed()} tracks the current backing world.
 */
public final class LiveWorld implements PracticeWorld {
    private final String handleId;
    private final PracticeDimension dimension;
    private ServerWorld world;
    private long seed;

    public LiveWorld(ServerWorld world, long seed, PracticeDimension dimension) {
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

    public synchronized void rebind(ServerWorld world, long seed) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        this.world = world;
        this.seed = seed;
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
    public PracticeDimension dimension() {
        return dimension;
    }

    @Override
    public GameVersion version() {
        return GameVersion.MC_1_16_1;
    }
}
