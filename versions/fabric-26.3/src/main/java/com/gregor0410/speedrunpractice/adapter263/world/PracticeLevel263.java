package com.gregor0410.speedrunpractice.adapter263.world;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Practice level with its own worldgen seed. Vanilla reads the seed from the
 * world's global options ({@code ServerLevel.getSeed} returns the save's
 * seed), so per-seed practice worlds need this override; the chunk storage
 * ({@code ChunkMap}) seeds its {@code RandomState} and structure state from
 * {@code level.getSeed()}, which makes the override the single seeding point.
 *
 * <p>The override must already answer during the superclass constructor
 * (chunk storage is built there), before instance fields are assigned, so
 * construction goes through {@link #create}: the seed rides a thread-local
 * into the constructor and is captured into the final field. Practice worlds
 * are always created on the server thread, synchronously, so the handoff is
 * race-free.
 *
 * <p>Each level also remembers its vanilla dimension (overworld/nether/end)
 * plus the sibling practice levels of its triple, so portal mixins can keep
 * portal travel inside the practice set.
 */
public class PracticeLevel263 extends ServerLevel {
    private static final ThreadLocal<Long> CONSTRUCTING_SEED = new ThreadLocal<Long>();

    private final long practiceSeed;
    private final PracticeDimension dimension;
    private volatile Map<ResourceKey<Level>, ServerLevel> associated =
            Collections.<ResourceKey<Level>, ServerLevel>emptyMap();

    private PracticeLevel263(MinecraftServer server, Executor executor,
            LevelStorageSource.LevelStorageAccess storage, ServerLevelData data,
            ResourceKey<Level> key, LevelStem stem, boolean debug, long biomeSeed,
            List<CustomSpawner> spawners, boolean tickTime, long practiceSeed,
            PracticeDimension dimension) {
        super(server, executor, storage, data, key, stem, debug, biomeSeed, spawners, tickTime);
        Long constructing = CONSTRUCTING_SEED.get();
        this.practiceSeed = constructing != null ? constructing.longValue() : practiceSeed;
        this.dimension = dimension;
    }

    /**
     * Builds a practice level for {@code key}, seeded with
     * {@code practiceSeed}. Must run on the server thread.
     */
    public static PracticeLevel263 create(MinecraftServer server, Executor executor,
            LevelStorageSource.LevelStorageAccess storage, ServerLevelData data,
            ResourceKey<Level> key, LevelStem stem, boolean debug, long biomeSeed,
            List<CustomSpawner> spawners, boolean tickTime, long practiceSeed,
            PracticeDimension dimension) {
        if (server == null || storage == null || data == null || key == null || stem == null
                || dimension == null) {
            throw new IllegalArgumentException("practice level arguments must not be null");
        }
        CONSTRUCTING_SEED.set(Long.valueOf(practiceSeed));
        try {
            return new PracticeLevel263(server, executor, storage, data, key, stem, debug,
                    biomeSeed, spawners, tickTime, practiceSeed, dimension);
        } finally {
            CONSTRUCTING_SEED.remove();
        }
    }

    @Override
    public long getSeed() {
        Long constructing = CONSTRUCTING_SEED.get();
        return constructing != null ? constructing.longValue() : practiceSeed;
    }

    /** The vanilla dimension this practice level mirrors. */
    public PracticeDimension practiceDimension() {
        return dimension;
    }

    /** Vanilla level key for {@link #practiceDimension()}. */
    public ResourceKey<Level> vanillaKey() {
        switch (dimension) {
            case NETHER:
                return Level.NETHER;
            case END:
                return Level.END;
            case OVERWORLD:
            default:
                return Level.OVERWORLD;
        }
    }

    /**
     * Links the triple after all members exist: vanilla key to the sibling
     * practice level (including this level under its own vanilla key).
     */
    public void link(Map<ResourceKey<Level>, ServerLevel> siblings) {
        if (siblings == null) {
            throw new IllegalArgumentException("siblings must not be null");
        }
        this.associated = Collections.unmodifiableMap(
                new java.util.HashMap<ResourceKey<Level>, ServerLevel>(siblings));
    }

    /**
     * Sibling practice level for a vanilla dimension key, or null when
     * unlinked (single end worlds) or unknown.
     */
    public ServerLevel associatedLevel(ResourceKey<Level> vanillaKey) {
        if (vanillaKey == null) {
            return null;
        }
        return associated.get(vanillaKey);
    }

    /**
     * Raises the 5x5 obsidian spawn platform under {@link #END_SPAWN_POINT}
     * with three blocks of headroom, mirroring the legacy end start. The
     * platform chunk is loaded synchronously first so the blocks always
     * land, even on a freshly created level.
     */
    public static void buildObsidianPlatform(ServerLevel level) {
        net.minecraft.core.BlockPos center = END_SPAWN_POINT;
        level.getChunk(center);
        net.minecraft.world.level.block.state.BlockState obsidian =
                net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState air =
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(center.offset(dx, -1, dz), obsidian, 3);
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), air, 3);
                }
            }
        }
    }
}
