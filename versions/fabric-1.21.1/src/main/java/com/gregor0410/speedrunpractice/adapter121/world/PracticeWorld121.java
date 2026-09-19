package com.gregor0410.speedrunpractice.adapter121.world;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.spawner.SpecialSpawner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Practice world with its own worldgen seed. The seed override must already
 * answer during the superclass constructor (chunk storage is built there, and
 * it reads {@code getSeed()}), before instance fields are assigned, so
 * construction goes through {@link #create}: the seed rides a thread-local
 * into the constructor and is captured into the final field. Practice worlds
 * are always created on the server thread, synchronously, so the handoff is
 * race-free.
 *
 * <p>Each world also remembers its vanilla dimension (overworld/nether/end)
 * plus the sibling practice worlds of its triple, so portal handling can keep
 * travel inside the practice set.
 */
public class PracticeWorld121 extends ServerWorld {
    private static final ThreadLocal<Long> CONSTRUCTING_SEED = new ThreadLocal<Long>();

    private final long practiceSeed;
    private final PracticeDimension dimension;
    private volatile Map<RegistryKey<World>, ServerWorld> associated =
            Collections.<RegistryKey<World>, ServerWorld>emptyMap();

    private PracticeWorld121(MinecraftServer server, Executor workerExecutor,
            LevelStorage.Session session, ServerWorldProperties properties,
            RegistryKey<World> key, DimensionOptions options,
            WorldGenerationProgressListener progressListener, boolean debugWorld,
            long biomeSeed, List<SpecialSpawner> spawners, boolean shouldTickTime,
            RandomSequencesState randomSequences, long practiceSeed,
            PracticeDimension dimension) {
        super(server, workerExecutor, session, properties, key, options,
                progressListener, debugWorld, biomeSeed, spawners, shouldTickTime,
                randomSequences);
        this.practiceSeed = practiceSeed;
        this.dimension = dimension;
    }

    /**
     * Builds a practice world for {@code key}, seeded with
     * {@code practiceSeed}. Must run on the server thread.
     */
    public static PracticeWorld121 create(MinecraftServer server, Executor workerExecutor,
            LevelStorage.Session session, ServerWorldProperties properties,
            RegistryKey<World> key, DimensionOptions options,
            WorldGenerationProgressListener progressListener, boolean debugWorld,
            long biomeSeed, List<SpecialSpawner> spawners, boolean shouldTickTime,
            RandomSequencesState randomSequences, long practiceSeed,
            PracticeDimension dimension) {
        if (server == null || workerExecutor == null || session == null || properties == null
                || key == null || options == null || progressListener == null
                || spawners == null || randomSequences == null || dimension == null) {
            throw new IllegalArgumentException("practice world arguments must not be null");
        }
        CONSTRUCTING_SEED.set(Long.valueOf(practiceSeed));
        try {
            return new PracticeWorld121(server, workerExecutor, session, properties, key,
                    options, progressListener, debugWorld, biomeSeed, spawners,
                    shouldTickTime, randomSequences, practiceSeed, dimension);
        } finally {
            CONSTRUCTING_SEED.remove();
        }
    }

    @Override
    public long getSeed() {
        Long constructing = CONSTRUCTING_SEED.get();
        return constructing != null ? constructing.longValue() : practiceSeed;
    }

    /**
     * Practice seed currently under construction on this thread, or null.
     * Lets constructor-time bytecode (which cannot see the not-yet-assigned
     * field, and sometimes bypasses {@link #getSeed()} for the server-global
     * seed) use the practice seed anyway.
     */
    public static Long constructingSeed() {
        return CONSTRUCTING_SEED.get();
    }

    /** The vanilla dimension this practice world mirrors. */
    public PracticeDimension practiceDimension() {
        return dimension;
    }

    /** Vanilla world key for {@link #practiceDimension()}. */
    public RegistryKey<World> vanillaKey() {
        switch (dimension) {
            case NETHER:
                return World.NETHER;
            case END:
                return World.END;
            case OVERWORLD:
            default:
                return World.OVERWORLD;
        }
    }

    /**
     * Links the triple after all members exist: vanilla key to the sibling
     * practice world (including this world under its own vanilla key).
     */
    public void link(Map<RegistryKey<World>, ServerWorld> siblings) {
        if (siblings == null) {
            throw new IllegalArgumentException("siblings must not be null");
        }
        this.associated = Collections.unmodifiableMap(
                new HashMap<RegistryKey<World>, ServerWorld>(siblings));
    }

    /**
     * Sibling practice world for a vanilla dimension key, or null when
     * unlinked (single end worlds) or unknown.
     */
    public ServerWorld associatedLevel(RegistryKey<World> vanillaKey) {
        if (vanillaKey == null) {
            return null;
        }
        return associated.get(vanillaKey);
    }

    /**
     * Raises the 5x5 obsidian spawn platform under {@link #END_SPAWN_POS}
     * with three blocks of headroom, mirroring the legacy end start. The
     * platform chunk is loaded synchronously first so the blocks always
     * land, even on a freshly created world.
     */
    public static void buildObsidianPlatform(ServerWorld level) {
        BlockPos center = ServerWorld.END_SPAWN_POS;
        level.getChunk(center.getX() >> 4, center.getZ() >> 4);
        BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockState(center.add(dx, -1, dz), obsidian, 3);
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlockState(center.add(dx, dy, dz), air, 3);
                }
            }
        }
    }
}
