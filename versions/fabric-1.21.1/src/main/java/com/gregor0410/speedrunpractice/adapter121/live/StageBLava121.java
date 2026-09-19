package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.SpringFeature;
import net.minecraft.world.gen.feature.SpringFeatureConfig;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.tick.QueryableTickScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Stage-B lava verification for seed search (plan section 7): runs the
 * game's own lava-spring placement pipeline for a candidate seed over
 * noise-stage terrain columns, on search worker threads. Yarn port of the
 * verified 26.3 verifier ({@code StageBLava263}); every vanilla call below
 * was checked against the 1.21.1 decoration loop
 * ({@code ChunkGenerator.generateFeatures} plus
 * {@code PlacedFeatureIndexer.collectIndexedFeatures}, CFR-decompiled from
 * the mapped jar).
 *
 * <p>What is exact: the decoration seeding
 * ({@code setPopulationSeed}/{@code setDecoratorSeed} with the same
 * per-step feature indices vanilla computes through
 * {@link PlacedFeatureIndexer}), every placement modifier (including the
 * biome filter, which reads candidate-seeded biomes), and the
 * {@link SpringFeature} conditions themselves — all real game code with
 * the candidate seed.
 *
 * <p>What is approximated: the terrain substrate is noise-stage columns
 * ({@code getColumnSample}), without surface rules, carvers or structure
 * terrain adaptation, and only lava features run (other decoration such
 * as ores or trees is skipped). Surface rules swap the top block's look,
 * not its solidity, so spring conditions (air plus sturdy rock
 * neighbours) evaluate the same; caves, structures and other features
 * intersecting a spring site can theoretically flip a result either way,
 * which in-game spot checks of found seeds should confirm.
 *
 * <p>Cost: a full scan builds up to ~21k noise columns, so lava-only
 * searches over wide ranges are slow; combine lava with structure or
 * biome filters so Stage-B runs only for Stage-A survivors.
 *
 * <p>Only surface lava counts: a placement verifies the seed only when
 * it sits no deeper than {@value #SURFACE_MARGIN} blocks below the
 * local noise surface. Deep cave springs the player would never find
 * must not satisfy a "surface lava near spawn" query (headless probe
 * 2026-09-19: ungated Stage-B fired on y=-50..-5 springs for seeds
 * with zero surface lava).
 *
 * <p>Only reports lava it actually places: any failure or budget
 * exhaustion verifies nothing and the seed mismatches (false negatives
 * only, never false positives from the verifier itself).
 */
final class StageBLava121 {
    /** Horizontal radius around spawn inside which lava counts. */
    private static final int RADIUS = 64;
    /**
     * Block margin around the radius. Springs write exactly at their
     * origin inside the chunk, and lava flows a few blocks from the
     * source, so 6 blocks cover every chunk whose pool can reach inside
     * the radius (still 9x9 chunks).
     */
    private static final int MARGIN_BLOCKS = 6;
    /**
     * Safety valve on noise-column builds per verification, sized for
     * full coverage of the scanned chunks (9x9x256). A smaller budget
     * would silently stop covering later chunks and miss lava there.
     */
    private static final int MAX_COLUMNS = 21000;
    /**
     * How far below the local noise surface a spring source may sit and
     * still count as surface lava (spring sources embed in rock with one
     * escape face, and lava flows a few blocks from the source).
     */
    private static final int SURFACE_MARGIN = 8;

    private StageBLava121() {
    }

    /**
     * Verifies surface lava within {@value #RADIUS} blocks of
     * {@code spawn} for {@code seed}, using {@code generator} with a
     * candidate-seeded {@code noiseConfig}, {@code biomeSource} and
     * {@code sampler}.
     */
    static boolean verify(MinecraftServer server, ServerWorld overworld,
            ChunkGenerator generator, NoiseConfig noiseConfig, BiomeSource biomeSource,
            MultiNoiseUtil.MultiNoiseSampler sampler, BlockPos spawn, long seed) {
        if (!(generator instanceof NoiseChunkGenerator)) {
            return false;
        }
        DynamicRegistryManager registries = server.getRegistryManager();
        Registry<PlacedFeature> placed;
        try {
            placed = registries.get(RegistryKeys.PLACED_FEATURE);
        } catch (RuntimeException missing) {
            return false;
        }
        if (placed == null) {
            return false;
        }
        List<RegistryEntry<PlacedFeature>> springs = lavaSprings(placed);
        if (springs.isEmpty()) {
            return false;
        }
        List<RegistryEntry<Biome>> biomes;
        try {
            biomes = new ArrayList<RegistryEntry<Biome>>(generator.getBiomeSource().getBiomes());
            if (biomes.isEmpty()) {
                return false;
            }
        } catch (RuntimeException missing) {
            return false;
        }
        final ChunkGenerator indexedGenerator = generator;
        List<PlacedFeatureIndexer.IndexedFeatures> perStep;
        try {
            perStep = PlacedFeatureIndexer.collectIndexedFeatures(biomes,
                    new Function<RegistryEntry<Biome>, List<RegistryEntryList<PlacedFeature>>>() {
                        @Override
                        public List<RegistryEntryList<PlacedFeature>> apply(RegistryEntry<Biome> biome) {
                            return indexedGenerator.getGenerationSettings(biome).getFeatures();
                        }
                    }, true);
        } catch (RuntimeException missing) {
            return false;
        }
        List<Spring> targets = new ArrayList<Spring>();
        for (RegistryEntry<PlacedFeature> spring : springs) {
            Spring located = locate(perStep, spring.value());
            if (located != null) {
                targets.add(located);
            }
        }
        if (targets.isEmpty()) {
            return false;
        }
        // TEMP probe diagnostics: revert after verification.
        StringBuilder springIds = new StringBuilder();
        for (RegistryEntry<PlacedFeature> spring : springs) {
            springIds.append(placed.getId(spring.value())).append(' ');
        }
        SpeedrunLogger.warn("PROBELAVA121-DIAG springs=" + springs.size() + " targets="
                + targets.size() + " ids=" + springIds);
        ColumnLevel level = new ColumnLevel(server, overworld,
                (NoiseChunkGenerator) generator, noiseConfig, biomeSource, sampler, spawn, seed);
        ChunkRandom random =
                new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
        int minChunkX = ChunkSectionPos.getSectionCoord(spawn.getX() - RADIUS - MARGIN_BLOCKS);
        int maxChunkX = ChunkSectionPos.getSectionCoord(spawn.getX() + RADIUS + MARGIN_BLOCKS);
        int minChunkZ = ChunkSectionPos.getSectionCoord(spawn.getZ() - RADIUS - MARGIN_BLOCKS);
        int maxChunkZ = ChunkSectionPos.getSectionCoord(spawn.getZ() + RADIUS + MARGIN_BLOCKS);
        try {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    BlockPos origin = ChunkSectionPos.from(new ChunkPos(chunkX, chunkZ),
                            level.getBottomSectionCoord()).getMinPos();
                    long populationSeed =
                            random.setPopulationSeed(seed, origin.getX(), origin.getZ());
                    for (Spring target : targets) {
                        random.setDecoratorSeed(populationSeed, target.index, target.step);
                        target.placed.generate(level, generator, random, origin);
                        if (level.foundLava()) {
                            // TEMP probe diagnostics: revert after verification.
                            SpeedrunLogger.warn("PROBELAVA121-DIAG seed=" + seed + " lavaAt="
                                    + level.firstLava());
                            return true;
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

    /** Placed features whose feature is a lava spring (datapack-safe). */
    private static List<RegistryEntry<PlacedFeature>> lavaSprings(Registry<PlacedFeature> placed) {
        List<RegistryEntry<PlacedFeature>> out = new ArrayList<RegistryEntry<PlacedFeature>>();
        List<RegistryEntry.Reference<PlacedFeature>> entries;
        try {
            entries = placed.streamEntries().collect(
                    java.util.stream.Collectors.<RegistryEntry.Reference<PlacedFeature>>toList());
        } catch (RuntimeException missing) {
            return out;
        }
        for (RegistryEntry.Reference<PlacedFeature> holder : entries) {
            PlacedFeature feature;
            try {
                feature = holder.value();
            } catch (RuntimeException missing) {
                continue;
            }
            if (feature == null || feature.feature() == null) {
                continue;
            }
            ConfiguredFeature<?, ?> inner;
            try {
                inner = feature.feature().value();
            } catch (RuntimeException missing) {
                continue;
            }
            if (inner == null || !(inner.feature() instanceof SpringFeature)) {
                continue;
            }
            if (!(inner.config() instanceof SpringFeatureConfig)) {
                continue;
            }
            FluidState state = ((SpringFeatureConfig) inner.config()).state;
            if (state == null || state.isEmpty()) {
                continue;
            }
            Fluid type = state.getFluid();
            if (type == Fluids.LAVA || type == Fluids.FLOWING_LAVA) {
                out.add(holder);
            }
        }
        return out;
    }

    /** Decoration step plus vanilla feature index of one placed feature. */
    private static Spring locate(List<PlacedFeatureIndexer.IndexedFeatures> perStep,
            PlacedFeature target) {
        for (int step = 0; step < perStep.size(); step++) {
            PlacedFeatureIndexer.IndexedFeatures data = perStep.get(step);
            if (data == null || data.features() == null || !data.features().contains(target)) {
                continue;
            }
            try {
                return new Spring(target, data.indexMapping().applyAsInt(target), step);
            } catch (RuntimeException missing) {
                return null;
            }
        }
        return null;
    }

    private static final class Spring {
        final PlacedFeature placed;
        final int index;
        final int step;

        private Spring(PlacedFeature placed, int index, int step) {
            this.placed = placed;
            this.index = index;
            this.step = step;
        }
    }

    /**
     * A {@link StructureWorldAccess} over lazily built noise-stage columns
     * for the candidate seed. Reads serve the placement pipeline; writes are
     * recorded (lava writes are the verification signal) and visible to
     * later reads. Everything the spring path never touches delegates to
     * the live level or answers degenerately.
     */
    private static final class ColumnLevel implements StructureWorldAccess {
        private final MinecraftServer server;
        private final ServerWorld live;
        private final NoiseChunkGenerator generator;
        private final NoiseConfig noiseConfig;
        private final BiomeSource biomeSource;
        private final MultiNoiseUtil.MultiNoiseSampler sampler;
        private final BlockPos spawn;
        private final long seed;
        private final Map<Long, VerticalBlockSample> columns =
                new HashMap<Long, VerticalBlockSample>();
        private final Map<BlockPos, BlockState> overrides = new HashMap<BlockPos, BlockState>();
        private final List<BlockPos> lava = new ArrayList<BlockPos>();
        private int built;

        private ColumnLevel(MinecraftServer server, ServerWorld live,
                NoiseChunkGenerator generator, NoiseConfig noiseConfig, BiomeSource biomeSource,
                MultiNoiseUtil.MultiNoiseSampler sampler, BlockPos spawn, long seed) {
            this.server = server;
            this.live = live;
            this.generator = generator;
            this.noiseConfig = noiseConfig;
            this.biomeSource = biomeSource;
            this.sampler = sampler;
            this.spawn = spawn;
            this.seed = seed;
        }

        /**
         * Whether any recorded lava counts. Recording filters at write
         * time (radius plus surface floor), so this stays O(1) no matter
         * how many deep/out-of-range springs the scan places.
         */
        boolean foundLava() {
            return !lava.isEmpty();
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

        // TEMP probe diagnostics: revert after verification.
        BlockPos firstLava() {
            return lava.isEmpty() ? null : lava.get(0);
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
            if (pos.getY() < getBottomY() || pos.getY() >= getTopY()) {
                return Blocks.AIR.getDefaultState();
            }
            VerticalBlockSample column = column(pos.getX(), pos.getZ());
            if (column == null) {
                return Blocks.AIR.getDefaultState();
            }
            try {
                BlockState state = column.getState(pos.getY());
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
            if (isLava(state) && withinRadius(pos) && pos.getY() >= surfaceFloor(pos.getX(), pos.getZ())) {
                lava.add(pos.toImmutable());
            }
            return true;
        }

        private boolean withinRadius(BlockPos at) {
            long dx = (long) at.getX() - spawn.getX();
            long dz = (long) at.getZ() - spawn.getZ();
            return dx * dx + dz * dz <= (long) RADIUS * RADIUS;
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
        public boolean testFluidState(BlockPos pos, Predicate<FluidState> predicate) {
            return predicate.test(getFluidState(pos));
        }

        @Override
        public RegistryEntry<Biome> getBiome(BlockPos pos) {
            return biomeSource.getBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2, sampler);
        }

        @Override
        public RegistryEntry<Biome> getBiomeForNoiseGen(int x, int y, int z) {
            return biomeSource.getBiome(x, y, z, sampler);
        }

        @Override
        public RegistryEntry<Biome> getGeneratorStoredBiome(int x, int y, int z) {
            return biomeSource.getBiome(x, y, z, sampler);
        }

        @Override
        public int getTopY(Heightmap.Type type, int x, int z) {
            Predicate<BlockState> opaque;
            try {
                opaque = type.getBlockPredicate();
            } catch (RuntimeException missing) {
                return getBottomY();
            }
            // getTopY is exclusive; the game's own predicate decides opacity.
            for (int y = getTopY() - 1; y >= getBottomY(); y--) {
                if (opaque.test(getBlockState(new BlockPos(x, y, z)))) {
                    return y + 1;
                }
            }
            return getBottomY();
        }

        @Override
        public int getBottomY() {
            return live.getBottomY();
        }

        @Override
        public int getHeight() {
            return live.getHeight();
        }

        @Override
        public int getSectionIndex(int y) {
            return live.getSectionIndex(y);
        }

        @Override
        public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean create) {
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
        public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos,
                BlockEntityType<T> type) {
            return Optional.empty();
        }

        @Override
        public List<Entity> getOtherEntities(Entity except, Box box, Predicate<? super Entity> predicate) {
            return new ArrayList<Entity>();
        }

        @Override
        public <T extends Entity> List<T> getEntitiesByType(TypeFilter<Entity, T> filter, Box box,
                Predicate<? super T> predicate) {
            return new ArrayList<T>();
        }

        @Override
        public List<? extends PlayerEntity> getPlayers() {
            return new ArrayList<PlayerEntity>();
        }

        @Override
        public ServerWorld toServerWorld() {
            return live;
        }

        @Override
        public long getTickOrder() {
            return 0L;
        }

        @Override
        public QueryableTickScheduler<Block> getBlockTickScheduler() {
            return live.getBlockTickScheduler();
        }

        @Override
        public QueryableTickScheduler<Fluid> getFluidTickScheduler() {
            return live.getFluidTickScheduler();
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
        public MinecraftServer getServer() {
            return server;
        }

        @Override
        public ChunkManager getChunkManager() {
            return live.getChunkManager();
        }

        @Override
        public Random getRandom() {
            return live.getRandom();
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
        public void emitGameEvent(RegistryEntry<GameEvent> event, Vec3d pos,
                GameEvent.Emitter emitter) {
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
        public float getPhototaxisFavor(BlockPos pos) {
            return live.getPhototaxisFavor(pos);
        }

        @Override
        public DynamicRegistryManager getRegistryManager() {
            return server.getRegistryManager();
        }

        @Override
        public net.minecraft.resource.featuretoggle.FeatureSet getEnabledFeatures() {
            return live.getEnabledFeatures();
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
        public WorldBorder getWorldBorder() {
            return live.getWorldBorder();
        }

        private static boolean isLava(BlockState state) {
            if (state.isOf(Blocks.LAVA)) {
                return true;
            }
            FluidState fluid = state.getFluidState();
            return fluid != null && !fluid.isEmpty()
                    && (fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA);
        }

        private VerticalBlockSample column(int x, int z) {
            long key = (((long) x) << 32) | (z & 0xFFFFFFFFL);
            VerticalBlockSample cached = columns.get(key);
            if (cached != null) {
                return cached;
            }
            if (built >= MAX_COLUMNS) {
                return null;
            }
            VerticalBlockSample fresh;
            try {
                fresh = generator.getColumnSample(x, z, live, noiseConfig);
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
    }
}
