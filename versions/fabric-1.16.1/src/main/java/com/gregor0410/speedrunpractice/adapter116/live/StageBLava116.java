package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.TickScheduler;
import net.minecraft.world.TickPriority;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGeneratorType;
import net.minecraft.world.gen.chunk.StructuresConfig;
import net.minecraft.world.gen.chunk.SurfaceChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.DecoratedFeatureConfig;
import net.minecraft.world.gen.feature.LakeFeature;
import net.minecraft.world.gen.feature.SingleStateFeatureConfig;
import net.minecraft.world.gen.feature.SpringFeature;
import net.minecraft.world.gen.feature.SpringFeatureConfig;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Stage-B lava verification for seed search on 1.16.1 (plan section 7):
 * runs the game's own lava-lake and lava-spring placement for a candidate
 * seed over noise-stage terrain columns, on search worker threads.
 *
 * <p>1.16.1 differs from the modern verifiers in two ways, both handled
 * here. First, surface lava comes from two features, not one: lava lakes
 * ({@code LAKES} step, {@link LakeFeature} with a lava state, 1/80 per
 * chunk) and lava springs ({@code VEGETAL_DECORATION} step,
 * {@link SpringFeature} with a lava state). Second, the decoration loop
 * is per-chunk single-biome ({@code ChunkGenerator.generateFeatures} plus
 * {@code Biome.generateFeatureStep}, verified against the 1.16.1
 * decompile): one chunk biome, {@code setPopulationSeed} per chunk, and
 * {@code setDecoratorSeed(populationSeed, index, step)} per feature where
 * the index counts structure features of that step first. Both are
 * replicated exactly, and every decorator and feature body executed is
 * real game code with the candidate seed.
 *
 * <p>What is approximated: the terrain substrate is noise-stage columns
 * ({@code SurfaceChunkGenerator.getColumnSample} on a freshly built
 * candidate-seed generator, the same construction practice worlds use),
 * without surface rules, carvers or structure terrain adaptation. Only
 * surface lava counts (no deeper than {@value #SURFACE_MARGIN} blocks
 * below the local noise surface), so deep cave springs can never verify
 * a seed. Caves, structures and other skipped decoration (ores, trees,
 * water lakes) intersecting the surface zone can theoretically flip a
 * result either way, which in-game spot checks of found seeds should
 * confirm.
 *
 * <p>Cost: a full scan builds up to ~44k noise columns, so lava-only
 * searches over wide ranges are slow; combine lava with structure or
 * biome filters so Stage-B runs only for Stage-A survivors.
 *
 * <p>Lakes inside villages are rejected by vanilla via a structure
 * lookup the column world cannot serve, so lake attempts within
 * {@value #VILLAGE_CLEARANCE} blocks of a real village start (same
 * placement math as {@link SeedAnalyzer116}) are skipped instead:
 * conservative false negatives, never false positives.
 *
 * <p>Only reports lava it actually places: any failure or budget
 * exhaustion verifies nothing and the seed mismatches (false negatives
 * only, never false positives from the verifier itself).
 */
final class StageBLava116 {
    /** Horizontal radius around spawn inside which lava counts. */
    private static final int RADIUS = 64;
    /**
     * Block margin around the radius. Lake basins carve up to 15 blocks
     * past their origin chunk, so 16 blocks cover every chunk that can
     * place lava inside the radius (11x11 chunks at most).
     */
    private static final int MARGIN_BLOCKS = 16;
    /**
     * Safety valve on noise-column builds per verification, sized for
     * full coverage of the scanned chunks (11x11x256). A smaller budget
     * would silently stop covering later chunks and miss lava there.
     */
    private static final int MAX_COLUMNS = 44000;
    /**
     * How far below the local noise surface lava may sit and still count
     * as surface lava (lake basins carve a few blocks down, and spring
     * sources embed in rock with one escape face).
     */
    private static final int SURFACE_MARGIN = 8;
    /**
     * Lake attempts this close to a village start are skipped: vanilla
     * rejects lakes intersecting villages, and the column world cannot
     * serve that lookup. Covers the basin plus the largest villages.
     */
    private static final int VILLAGE_CLEARANCE = 224;
    /** 1.16.1 build height (fixed, no height accessor yet). */
    private static final int WORLD_HEIGHT = 256;

    private StageBLava116() {
    }

    /**
     * Verifies surface lava within {@value #RADIUS} blocks of
     * {@code spawn} for {@code seed}. {@code overSource} and
     * {@code overConfig} are the candidate-seed overworld source and the
     * live structures config, shared with the Stage-A pass.
     */
    static boolean verify(MinecraftServer server, ServerWorld overworld, long seed,
            BlockPos spawn, BiomeSource overSource, StructuresConfig overConfig) {
        SurfaceChunkGenerator generator;
        try {
            generator = new SurfaceChunkGenerator(
                    new VanillaLayeredBiomeSource(seed, false, false), seed,
                    ChunkGeneratorType.Preset.OVERWORLD.getChunkGeneratorType());
        } catch (RuntimeException badGenerator) {
            return false;
        }
        Map<GenerationStep.Feature, Integer> structBase = structureBases();
        ChunkPos village = null;
        try {
            village = SeedAnalyzer116.nearestVillageStart(seed, overSource, overConfig, spawn,
                    RADIUS + MARGIN_BLOCKS + VILLAGE_CLEARANCE);
        } catch (RuntimeException badVillage) {
            return false;
        }
        ColumnLevel level = new ColumnLevel(server, overworld, generator, overSource, spawn, seed);
        StructureAccessor accessor;
        try {
            accessor = new StructureAccessor(level, server.getSaveProperties().getGeneratorOptions()) {
                @Override
                public Stream<? extends StructureStart<?>> getStructuresWithChildren(
                        ChunkSectionPos pos, StructureFeature<?> feature) {
                    // Villages are gated outside (proximity skip); nothing
                    // else the lava path touches reads structures.
                    return Stream.empty();
                }
            };
        } catch (RuntimeException badAccessor) {
            return false;
        }
        ChunkRandom random = new ChunkRandom();
        int minChunkX = Math.floorDiv(spawn.getX() - RADIUS - MARGIN_BLOCKS, 16);
        int maxChunkX = Math.floorDiv(spawn.getX() + RADIUS + MARGIN_BLOCKS, 16);
        int minChunkZ = Math.floorDiv(spawn.getZ() - RADIUS - MARGIN_BLOCKS, 16);
        int maxChunkZ = Math.floorDiv(spawn.getZ() + RADIUS + MARGIN_BLOCKS, 16);
        GenerationStep.Feature[] steps = GenerationStep.Feature.values();
        try {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    BlockPos origin = new BlockPos(chunkX * 16, 0, chunkZ * 16);
                    Biome biome = overSource.getBiomeForNoiseGen(
                            (chunkX << 2) + 2, 2, (chunkZ << 2) + 2);
                    if (biome == null) {
                        continue;
                    }
                    long populationSeed =
                            random.setPopulationSeed(seed, origin.getX(), origin.getZ());
                    for (GenerationStep.Feature step : steps) {
                        List<ConfiguredFeature<?, ?>> features;
                        try {
                            features = biome.getFeaturesForStep(step);
                        } catch (RuntimeException badBiome) {
                            continue;
                        }
                        if (features == null || features.isEmpty()) {
                            continue;
                        }
                        int base = structBase.containsKey(step) ? structBase.get(step) : 0;
                        for (int index = 0; index < features.size(); index++) {
                            ConfiguredFeature<?, ?> configured = features.get(index);
                            LavaKind kind = classify(configured);
                            if (kind == null) {
                                continue;
                            }
                            if (kind == LavaKind.LAKE && village != null
                                    && villageNear(village, chunkX, chunkZ)) {
                                continue;
                            }
                            random.setDecoratorSeed(populationSeed, base + index, step.ordinal());
                            configured.generate(level, accessor, generator, random, origin);
                            if (level.foundLava()) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Stage-B lava check failed for seed " + seed + ": " + failure);
            return false;
        }
        return level.foundLava();
    }

    /** Per-step structure-feature counts (vanilla seeds them first). */
    private static Map<GenerationStep.Feature, Integer> structureBases() {
        Map<GenerationStep.Feature, Integer> bases =
                new HashMap<GenerationStep.Feature, Integer>();
        for (StructureFeature<?> feature : Registry.STRUCTURE_FEATURE) {
            GenerationStep.Feature step;
            try {
                step = feature.method_28663();
            } catch (RuntimeException badFeature) {
                continue;
            }
            if (step == null) {
                continue;
            }
            Integer base = bases.get(step);
            bases.put(step, base == null ? 1 : base + 1);
        }
        return bases;
    }

    private static boolean villageNear(ChunkPos village, int chunkX, int chunkZ) {
        long dx = (long) (village.x * 16 + 8) - (chunkX * 16 + 8);
        long dz = (long) (village.z * 16 + 8) - (chunkZ * 16 + 8);
        return dx * dx + dz * dz <= (long) VILLAGE_CLEARANCE * VILLAGE_CLEARANCE;
    }

    private enum LavaKind {
        SPRING,
        LAKE,
    }

    /**
     * Classifies a biome feature as a lava spring, a lava lake, or
     * neither. Handles both decorated entries (the vanilla shape) and
     * bare spring/lake features (datapack shapes).
     */
    private static LavaKind classify(ConfiguredFeature<?, ?> configured) {
        if (configured == null || configured.feature == null) {
            return null;
        }
        if (configured.config instanceof DecoratedFeatureConfig) {
            ConfiguredFeature<?, ?> inner = ((DecoratedFeatureConfig) configured.config).feature;
            return classifyInner(inner);
        }
        return classifyInner(configured);
    }

    private static LavaKind classifyInner(ConfiguredFeature<?, ?> configured) {
        if (configured == null || configured.feature == null) {
            return null;
        }
        if (configured.feature instanceof SpringFeature
                && configured.config instanceof SpringFeatureConfig) {
            FluidState state = ((SpringFeatureConfig) configured.config).state;
            if (state != null && !state.isEmpty()
                    && (state.getFluid() == Fluids.LAVA || state.getFluid() == Fluids.FLOWING_LAVA)) {
                return LavaKind.SPRING;
            }
            return null;
        }
        if (configured.feature instanceof LakeFeature
                && configured.config instanceof SingleStateFeatureConfig) {
            BlockState state = ((SingleStateFeatureConfig) configured.config).state;
            // The same predicate LakeFeature itself uses for its lava rim.
            if (state != null && state.getMaterial() == Material.LAVA) {
                return LavaKind.LAKE;
            }
        }
        return null;
    }

    /**
     * A {@link ServerWorldAccess} over lazily built noise-stage columns
     * for the candidate seed. Reads serve the placement pipeline; writes
     * are recorded (lava writes are the verification signal) and visible
     * to later reads. Everything the lava path never touches delegates to
     * the live level or answers degenerately.
     */
    private static final class ColumnLevel implements ServerWorldAccess {
        private final MinecraftServer server;
        private final ServerWorld live;
        private final SurfaceChunkGenerator generator;
        private final BiomeSource source;
        private final BlockPos spawn;
        private final long seed;
        private final Map<Long, BlockView> columns = new HashMap<Long, BlockView>();
        private final Map<BlockPos, BlockState> overrides = new HashMap<BlockPos, BlockState>();
        private final List<BlockPos> lava = new ArrayList<BlockPos>();
        private final TickScheduler<Block> blockTicks = new NoopTicks<Block>();
        private final TickScheduler<Fluid> fluidTicks = new NoopTicks<Fluid>();
        private final Random random = new Random();
        private int built;

        private ColumnLevel(MinecraftServer server, ServerWorld live,
                SurfaceChunkGenerator generator, BiomeSource source, BlockPos spawn, long seed) {
            this.server = server;
            this.live = live;
            this.generator = generator;
            this.source = source;
            this.spawn = spawn;
            this.seed = seed;
        }

        /**
         * Whether any recorded lava counts. Recording filters at write
         * time (radius plus surface floor), so this stays O(1) no matter
         * how many deep/out-of-range lakes and springs the scan places.
         */
        boolean foundLava() {
            return !lava.isEmpty();
        }

        @Override
        public long getSeed() {
            return seed;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState override = overrides.get(pos);
            if (override != null) {
                return override;
            }
            if (pos.getY() < 0 || pos.getY() >= WORLD_HEIGHT) {
                return Blocks.AIR.getDefaultState();
            }
            BlockView column = column(pos.getX(), pos.getZ());
            if (column == null) {
                return Blocks.AIR.getDefaultState();
            }
            try {
                BlockState state = column.getBlockState(pos);
                return state == null ? Blocks.AIR.getDefaultState() : state;
            } catch (RuntimeException outOfRange) {
                return Blocks.AIR.getDefaultState();
            }
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public boolean setBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
            if (state == null) {
                return false;
            }
            overrides.put(pos.toImmutable(), state);
            if (isLava(state) && withinRadius(pos)
                    && pos.getY() >= surfaceFloor(pos.getX(), pos.getZ())) {
                lava.add(pos.toImmutable());
            }
            return true;
        }

        private boolean withinRadius(BlockPos at) {
            long dx = (long) at.getX() - spawn.getX();
            long dz = (long) at.getZ() - spawn.getZ();
            return dx * dx + dz * dz <= (long) RADIUS * RADIUS;
        }

        /**
         * Lowest Y that still counts as surface lava at a column: the
         * local noise surface minus {@value #SURFACE_MARGIN}. Fails
         * closed (unreadable surface verifies nothing).
         */
        private int surfaceFloor(int x, int z) {
            try {
                return getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - SURFACE_MARGIN;
            } catch (RuntimeException unreadable) {
                return Integer.MAX_VALUE;
            }
        }

        @Override
        public boolean removeBlock(BlockPos pos, boolean move) {
            overrides.put(pos.toImmutable(), Blocks.AIR.getDefaultState());
            return true;
        }

        @Override
        public boolean breakBlock(BlockPos pos, boolean drop, Entity entity, int maxUpdateDepth) {
            return removeBlock(pos, false);
        }

        @Override
        public boolean testBlockState(BlockPos pos, Predicate<BlockState> predicate) {
            return predicate.test(getBlockState(pos));
        }

        @Override
        public BlockPos getTopPosition(Heightmap.Type type, BlockPos pos) {
            return new BlockPos(pos.getX(), getTopY(type, pos.getX(), pos.getZ()), pos.getZ());
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return source.getBiomeForNoiseGen(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        }

        @Override
        public Biome getBiomeForNoiseGen(int x, int y, int z) {
            return source.getBiomeForNoiseGen(x, y, z);
        }

        @Override
        public Biome getGeneratorStoredBiome(int x, int y, int z) {
            return source.getBiomeForNoiseGen(x, y, z);
        }

        @Override
        public int getTopY(Heightmap.Type type, int x, int z) {
            for (int y = WORLD_HEIGHT - 1; y >= 0; y--) {
                if (heightmapOpaque(type, getBlockState(new BlockPos(x, y, z)))) {
                    return y + 1;
                }
            }
            return 0;
        }

        @Override
        public int getLightLevel(LightType type, BlockPos pos) {
            // Surface lakes see the sky; light only shapes the grass rim,
            // never the lava itself.
            return 15;
        }

        @Override
        public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean create) {
            throw new UnsupportedOperationException("Stage-B serves columns, not chunks");
        }

        @Override
        public net.minecraft.world.BlockView getExistingChunk(int chunkX, int chunkZ) {
            throw new UnsupportedOperationException("Stage-B serves columns, not chunks");
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public List<Entity> getEntities(Entity except, Box box, Predicate<? super Entity> predicate) {
            return new ArrayList<Entity>();
        }

        @Override
        public <T extends Entity> List<T> getEntities(Class<? extends T> type, Box box,
                Predicate<? super T> predicate) {
            return new ArrayList<T>();
        }

        @Override
        public List<? extends PlayerEntity> getPlayers() {
            return new ArrayList<PlayerEntity>();
        }

        @Override
        public Stream<VoxelShape> getEntityCollisions(Entity entity, Box box,
                Predicate<Entity> predicate) {
            return Stream.empty();
        }

        @Override
        public TickScheduler<Block> getBlockTickScheduler() {
            return blockTicks;
        }

        @Override
        public TickScheduler<Fluid> getFluidTickScheduler() {
            return fluidTicks;
        }

        @Override
        public World getWorld() {
            return live;
        }

        @Override
        public WorldProperties getLevelProperties() {
            return live.getLevelProperties();
        }

        @Override
        public LocalDifficulty getLocalDifficulty(BlockPos pos) {
            return live.getLocalDifficulty(pos);
        }

        @Override
        public ChunkManager getChunkManager() {
            return live.getChunkManager();
        }

        @Override
        public Random getRandom() {
            return random;
        }

        @Override
        public void playSound(PlayerEntity source, BlockPos pos, SoundEvent event,
                SoundCategory category, float volume, float pitch) {
        }

        @Override
        public void addParticle(ParticleEffect parameters, double x, double y, double z,
                double velocityX, double velocityY, double velocityZ) {
        }

        @Override
        public void syncWorldEvent(PlayerEntity player, int eventId, BlockPos pos, int data) {
        }

        @Override
        public int getAmbientDarkness() {
            return live.getAmbientDarkness();
        }

        @Override
        public BiomeAccess getBiomeAccess() {
            return live.getBiomeAccess();
        }

        @Override
        public boolean isClient() {
            return false;
        }

        @Override
        public int getSeaLevel() {
            return live.getSeaLevel();
        }

        @Override
        public DimensionType getDimension() {
            return live.getDimension();
        }

        @Override
        public float getBrightness(Direction direction, boolean shaded) {
            return live.getBrightness(direction, shaded);
        }

        @Override
        public LightingProvider getLightingProvider() {
            return live.getLightingProvider();
        }

        @Override
        public int getColor(BlockPos pos,
                net.minecraft.world.level.ColorResolver colorResolver) {
            return live.getColor(pos, colorResolver);
        }

        @Override
        public WorldBorder getWorldBorder() {
            return live.getWorldBorder();
        }

        /**
         * Mirrors the game's heightmap predicates: world-surface maps stop
         * at any non-air block, ocean-floor maps at solid material, and
         * motion-blocking maps also stop at fluids.
         */
        private static boolean heightmapOpaque(Heightmap.Type type, BlockState state) {
            if (type == Heightmap.Type.WORLD_SURFACE || type == Heightmap.Type.WORLD_SURFACE_WG) {
                return !state.isAir();
            }
            if (state.getMaterial().isSolid()) {
                return true;
            }
            return type == Heightmap.Type.MOTION_BLOCKING
                    || type == Heightmap.Type.MOTION_BLOCKING_NO_LEAVES
                    ? !state.getFluidState().isEmpty() : false;
        }

        private static boolean isLava(BlockState state) {
            if (state.getBlock() == Blocks.LAVA) {
                return true;
            }
            FluidState fluid = state.getFluidState();
            return fluid != null && !fluid.isEmpty()
                    && (fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA);
        }

        private BlockView column(int x, int z) {
            long key = (((long) x) << 32) | (z & 0xFFFFFFFFL);
            BlockView cached = columns.get(key);
            if (cached != null) {
                return cached;
            }
            if (built >= MAX_COLUMNS) {
                return null;
            }
            BlockView fresh;
            try {
                fresh = generator.getColumnSample(x, z);
            } catch (RuntimeException badColumn) {
                return null;
            }
            if (fresh == null) {
                return null;
            }
            built++;
            columns.put(key, fresh);
            return fresh;
        }

        private static final class NoopTicks<T> implements TickScheduler<T> {
            @Override
            public boolean isScheduled(BlockPos pos, T entry) {
                return false;
            }

            @Override
            public void schedule(BlockPos pos, T entry, int delay, TickPriority priority) {
            }

            @Override
            public boolean isTicking(BlockPos pos, T entry) {
                return false;
            }
        }
    }
}
