package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.server.level.ServerLevel;

/**
 * Live 26.3 world handle. Mutable by design: {@code resetPracticeWorld}
 * rebinds the backing {@link ServerLevel} in place while the context keeps
 * holding this handle, so the handle id stays fixed for its lifetime (event
 * correlation) and {@link #seed()} tracks the current practice seed.
 *
 * <p>26.3 reuses the server's live vanilla levels (one per dimension); there
 * is no per-seed world recreation yet. The handle seed is the requested
 * practice seed, which may differ from the backing level's worldgen seed
 * until that follow-up lands.
 */
public final class LiveWorld263 implements PracticeWorld {
    private final String handleId;
    private ServerLevel level;
    private long seed;
    private PracticeDimension dimension;

    public LiveWorld263(ServerLevel level, long seed, PracticeDimension dimension) {
        if (level == null || dimension == null) {
            throw new IllegalArgumentException("level and dimension must not be null");
        }
        this.level = level;
        this.seed = seed;
        this.dimension = dimension;
        this.handleId = level.dimension().identifier().toString();
    }

    public synchronized ServerLevel level() {
        return level;
    }

    public synchronized void rebind(ServerLevel level, long seed, PracticeDimension dimension) {
        if (level == null || dimension == null) {
            throw new IllegalArgumentException("level and dimension must not be null");
        }
        this.level = level;
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
        return GameVersion.V_26_3;
    }
}
