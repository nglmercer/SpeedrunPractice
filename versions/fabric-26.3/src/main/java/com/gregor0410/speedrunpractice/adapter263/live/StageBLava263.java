package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage-B lava verification for seed search (plan section 7): runs the
 * game's own lava-spring placement pipeline for a candidate seed over
 * noise-stage terrain columns, on search worker threads.
 *
 * <p>What is exact: the decoration seeding
 * ({@code setDecorationSeed}/{@code setFeatureSeed} with the same feature
 * indices vanilla computes through {@link FeatureSorter}), every placement
 * modifier, the biome checks (candidate-seeded climate sampler), and the
 * {@link SpringFeature} conditions themselves — all real game code with
 * the candidate seed.
 *
 * <p>What is approximated: the terrain substrate is noise-stage columns
 * ({@code getBaseColumn}), without surface rules, carvers or structure
 * terrain adaptation. Surface rules swap the top block's look, not its
 * solidity, so spring conditions (air plus sturdy rock neighbours)
 * evaluate the same; caves and structures intersecting a spring site can
 * theoretically flip a result either way, which in-game spot checks of
 * found seeds should confirm.
 *
 * <p>Only reports lava it actually places: any failure or budget
 * exhaustion verifies nothing and the seed mismatches (false negatives
 * only, never false positives from the verifier itself).
 */
final class StageBLava263 {
    /** Horizontal radius around spawn inside which lava counts. */
    private static final int RADIUS = 64;
    /** Chunk margin around the radius so spring origins are covered. */
    private static final int MARGIN_CHUNKS = 1;
    /** Safety valve on noise-column builds per verification. */
    private static final int MAX_COLUMNS = 8192;

    private StageBLava263() {
    }

    /**
     * Verifies surface lava within {@value #RADIUS} blocks of
     * {@code spawn} for {@code seed}, using {@code generator} with a
     * candidate-seeded {@code randomState} and {@code resolver}.
     */
    static boolean verify(MinecraftServer server, ServerLevel overworld,
            ChunkGenerator generator, RandomState randomState, BiomeResolver resolver,
            BlockPos spawn, long seed) {
        if (!(generator instanceof NoiseBasedChunkGenerator)) {
            return false;
        }
        RegistryAccess registries = server.registryAccess();
        HolderLookup<PlacedFeature> placed;
        try {
            placed = registries.lookupOrThrow(Registries.PLACED_FEATURE);
        } catch (RuntimeException missing) {
            return false;
        }
        List<Holder<PlacedFeature>> springs = lavaSprings(placed);
        if (springs.isEmpty()) {
            return false;
        }
        List<Holder<Biome>> biomes;
        try {
            biomes = new ArrayList<Holder<Biome>>(generator.getBiomeSource().possibleBiomes());
            if (biomes.isEmpty()) {
                return false;
            }
        } catch (RuntimeException missing) {
            return false;
        }
        List<FeatureSorter.StepFeatureData> perStep;
        try {
            perStep = FeatureSorter.buildFeaturesPerStep(biomes,
                    new java.util.function.Function<Holder<Biome>,
                            List<net.minecraft.core.HolderSet<PlacedFeature>>>() {
                        @Override
                        public List<net.minecraft.core.HolderSet<PlacedFeature>> apply(
                                Holder<Biome> biome) {
                            return generator.getBiomeGenerationSettings(biome).features();
                        }
                    }, true);
        } catch (RuntimeException missing) {
            return false;
        }
        List<Spring> targets = new ArrayList<Spring>();
        for (Holder<PlacedFeature> spring : springs) {
            Spring located = locate(perStep, spring.value());
            if (located != null) {
                targets.add(located);
            }
        }
        if (targets.isEmpty()) {
            return false;
        }
        ColumnLevel level = new ColumnLevel(server, overworld,
                (NoiseBasedChunkGenerator) generator, randomState, resolver, seed, spawn);
        FeaturePlacer placer = new FeaturePlacer(level, generator);
        WorldgenRandom random =
                new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        int minChunkX = SectionPos.blockToSectionCoord(spawn.getX() - RADIUS - MARGIN_CHUNKS * 16);
        int maxChunkX = SectionPos.blockToSectionCoord(spawn.getX() + RADIUS + MARGIN_CHUNKS * 16);
        int minChunkZ = SectionPos.blockToSectionCoord(spawn.getZ() - RADIUS - MARGIN_CHUNKS * 16);
        int maxChunkZ = SectionPos.blockToSectionCoord(spawn.getZ() + RADIUS + MARGIN_CHUNKS * 16);
        try {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    BlockPos origin = SectionPos.of(new ChunkPos(chunkX, chunkZ),
                            level.getMinSectionY()).origin();
                    long decorationSeed = random.setDecorationSeed(seed, origin.getX(), origin.getZ());
                    for (Spring target : targets) {
                        random.setFeatureSeed(decorationSeed, target.index, target.step);
                        placer.placeWithBiomeCheck(target.placed, random, origin);
                        if (level.foundLava(spawn)) {
                            return true;
                        }
                    }
                }
            }
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Stage-B lava check failed for seed " + seed + ": " + failure);
            return false;
        }
        return level.foundLava(spawn);
    }

    /** Placed features whose feature is a lava spring (datapack-safe). */
    private static List<Holder<PlacedFeature>> lavaSprings(HolderLookup<PlacedFeature> placed) {
        List<Holder<PlacedFeature>> out = new ArrayList<Holder<PlacedFeature>>();
        for (Holder<PlacedFeature> holder : placed.listElements().toList()) {
            PlacedFeature feature = holder.value();
            if (feature == null || feature.feature() == null) {
                continue;
            }
            Holder<net.minecraft.world.level.levelgen.feature.Feature> inner = feature.feature();
            if (inner == null || !(inner.value() instanceof SpringFeature)) {
                continue;
            }
            FluidState state = ((SpringFeature) inner.value()).state();
            if (state == null || state.isEmpty()) {
                continue;
            }
            Fluid type = state.getType();
            if (type == Fluids.LAVA || type == Fluids.FLOWING_LAVA) {
                out.add(holder);
            }
        }
        return out;
    }

    /** Decoration step plus vanilla feature index of one placed feature. */
    private static Spring locate(List<FeatureSorter.StepFeatureData> perStep, PlacedFeature target) {
        for (int step = 0; step < perStep.size(); step++) {
            FeatureSorter.StepFeatureData data = perStep.get(step);
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
     * A {@link WorldGenLevel} over lazily built noise-stage columns for
     * the candidate seed. Reads serve the placement pipeline; writes are
     * recorded (lava writes are the verification signal) and visible to
     * later reads. Everything the spring path never touches delegates to
     * the live level or answers degenerately.
     */
    private static final class ColumnLevel implements WorldGenLevel {
        private final MinecraftServer server;
        private final ServerLevel live;
        private final NoiseBasedChunkGenerator generator;
        private final RandomState randomState;
        private final BiomeResolver resolver;
        private final long seed;
        private final Map<Long, net.minecraft.world.level.NoiseColumn> columns =
                new HashMap<Long, net.minecraft.world.level.NoiseColumn>();
        private final Map<BlockPos, BlockState> overrides = new HashMap<BlockPos, BlockState>();
        private final List<BlockPos> lava = new ArrayList<BlockPos>();
        private int built;

        private ColumnLevel(MinecraftServer server, ServerLevel live,
                NoiseBasedChunkGenerator generator, RandomState randomState,
                BiomeResolver resolver, long seed, BlockPos spawn) {
            this.server = server;
            this.live = live;
            this.generator = generator;
            this.randomState = randomState;
            this.resolver = resolver;
            this.seed = seed;
        }

        boolean foundLava(BlockPos spawn) {
            for (BlockPos at : lava) {
                long dx = (long) at.getX() - spawn.getX();
                long dz = (long) at.getZ() - spawn.getZ();
                if (dx * dx + dz * dz <= (long) RADIUS * RADIUS) {
                    return true;
                }
            }
            return false;
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
            if (pos.getY() < getMinY() || pos.getY() > getMaxY()) {
                return Blocks.AIR.defaultBlockState();
            }
            net.minecraft.world.level.NoiseColumn column = column(pos.getX(), pos.getZ());
            if (column == null) {
                return Blocks.AIR.defaultBlockState();
            }
            try {
                BlockState state = column.getBlock(pos.getY());
                return state == null ? Blocks.AIR.defaultBlockState() : state;
            } catch (RuntimeException outOfRange) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public boolean isEmptyBlock(BlockPos pos) {
            return getBlockState(pos).isAir();
        }

        @Override
        public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            if (state == null) {
                return false;
            }
            overrides.put(pos.immutable(), state);
            if (isLava(state)) {
                lava.add(pos.immutable());
            }
            return true;
        }

        @Override
        public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLimit) {
            return setBlock(pos, state, flags);
        }

        @Override
        public boolean removeBlock(BlockPos pos, boolean move) {
            overrides.put(pos.immutable(), Blocks.AIR.defaultBlockState());
            return true;
        }

        @Override
        public boolean destroyBlock(BlockPos pos, boolean drop, Entity entity, int recursionLimit) {
            return removeBlock(pos, false);
        }

        @Override
        public Holder<Biome> getBiome(BlockPos pos) {
            return resolver.getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                    QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
        }

        @Override
        public int getHeight(Heightmap.Types type, int x, int z) {
            for (int y = getMaxY(); y >= getMinY(); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = getBlockState(pos);
                if (heightmapOpaque(type, state)) {
                    return y + 1;
                }
            }
            return getMinY();
        }

        @Override
        public int getMinY() {
            return live.getMinY();
        }

        @Override
        public int getHeight() {
            return live.getHeight();
        }

        @Override
        public int getMinSectionY() {
            return live.getMinSectionY();
        }

        @Override
        public int getMaxSectionY() {
            return live.getMaxSectionY();
        }

        @Override
        public boolean isOutsideBuildHeight(BlockPos pos) {
            return live.isOutsideBuildHeight(pos);
        }

        @Override
        public RegistryAccess registryAccess() {
            return server.registryAccess();
        }

        @Override
        public ServerLevel getLevel() {
            return live;
        }

        @Override
        public MinecraftServer getServer() {
            return server;
        }

        @Override
        public LevelData getLevelData() {
            return live.getLevelData();
        }

        @Override
        public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
            return live.getCurrentDifficultyAt(pos);
        }

        @Override
        public Difficulty getDifficulty() {
            return live.getDifficulty();
        }

        @Override
        public ChunkSource getChunkSource() {
            return live.getChunkSource();
        }

        @Override
        public RandomSource getRandom() {
            return live.getRandom();
        }

        @Override
        public long nextSubTickCount() {
            return 0L;
        }

        @Override
        public void scheduleTick(BlockPos pos, Block block, int delay) {
        }

        @Override
        public void scheduleTick(BlockPos pos, Fluid fluid, int delay) {
        }

        @Override
        public net.minecraft.world.ticks.LevelTickAccess<Block> getBlockTicks() {
            return live.getBlockTicks();
        }

        @Override
        public net.minecraft.world.ticks.LevelTickAccess<Fluid> getFluidTicks() {
            return live.getFluidTicks();
        }

        @Override
        public void playSound(Entity entity, BlockPos pos, SoundEvent event, SoundSource source,
                float volume, float pitch) {
        }

        @Override
        public void addParticle(ParticleOptions options, double x, double y, double z,
                double velocityX, double velocityY, double velocityZ) {
        }

        @Override
        public void levelEvent(Entity entity, int id, BlockPos pos, int data) {
        }

        @Override
        public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
        }

        @Override
        public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
            return live.enabledFeatures();
        }

        @Override
        public DimensionType dimensionType() {
            return live.dimensionType();
        }

        @Override
        public net.minecraft.world.level.chunk.ChunkAccess getChunk(int chunkX, int chunkZ,
                net.minecraft.world.level.chunk.status.ChunkStatus status, boolean create) {
            throw new UnsupportedOperationException("Stage-B serves columns, not chunks");
        }

        @Override
        public int getSkyDarken() {
            return live.getSkyDarken();
        }

        @Override
        public net.minecraft.world.level.biome.BiomeManager getBiomeManager() {
            return live.getBiomeManager();
        }

        @Override
        public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
            return resolver.getNoiseBiome(x, y, z);
        }

        @Override
        public boolean isClientSide() {
            return false;
        }

        @Override
        public int getSeaLevel() {
            return live.getSeaLevel();
        }

        @Override
        public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
            return live.getLightEngine();
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
            return live.getWorldBorder();
        }

        @Override
        public net.minecraft.world.level.BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
            return live.getChunkForCollisions(chunkX, chunkZ);
        }

        @Override
        public List<net.minecraft.world.phys.shapes.VoxelShape> getEntityCollisions(Entity entity,
                net.minecraft.world.phys.AABB box) {
            return live.getEntityCollisions(entity, box);
        }

        @Override
        public boolean isStateAtPosition(BlockPos pos,
                java.util.function.Predicate<BlockState> predicate) {
            return predicate.test(getBlockState(pos));
        }

        @Override
        public boolean isFluidAtPosition(BlockPos pos,
                java.util.function.Predicate<FluidState> predicate) {
            return predicate.test(getFluidState(pos));
        }

        @Override
        public <T extends net.minecraft.world.level.block.entity.BlockEntity> java.util.Optional<T>
                getBlockEntity(BlockPos pos,
                        net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
            return java.util.Optional.empty();
        }

        @Override
        public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) {
            return new BlockPos(pos.getX(), getHeight(type, pos.getX(), pos.getZ()), pos.getZ());
        }

        @Override
        public List<Entity> getEntities(Entity except,
                net.minecraft.world.phys.AABB box,
                java.util.function.Predicate<? super Entity> predicate) {
            return new ArrayList<Entity>();
        }

        @Override
        public <T extends Entity> List<T> getEntities(
                net.minecraft.world.level.entity.EntityTypeTest<Entity, T> test,
                net.minecraft.world.phys.AABB box,
                java.util.function.Predicate<? super T> predicate) {
            return new ArrayList<T>();
        }

        @Override
        public List<? extends net.minecraft.world.entity.player.Player> players() {
            return new ArrayList<net.minecraft.world.entity.player.Player>();
        }

        @Override
        public boolean hasChunk(int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public net.minecraft.world.attribute.EnvironmentAttributeReader environmentAttributes() {
            return live.environmentAttributes();
        }

        /**
         * Mirrors the game's heightmap predicates: world-surface maps stop
         * at any non-air block, ocean-floor maps at motion-blocking
         * material, and motion-blocking maps also stop at fluids.
         */
        private static boolean heightmapOpaque(Heightmap.Types type, BlockState state) {
            if (type == Heightmap.Types.WORLD_SURFACE || type == Heightmap.Types.WORLD_SURFACE_WG) {
                return !state.isAir();
            }
            if (state.is(net.minecraft.tags.BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP)) {
                return true;
            }
            return type == Heightmap.Types.MOTION_BLOCKING
                    || type == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
                    ? !state.getFluidState().isEmpty() : false;
        }

        private static boolean isLava(BlockState state) {
            if (state.is(Blocks.LAVA)) {
                return true;
            }
            FluidState fluid = state.getFluidState();
            return fluid != null && !fluid.isEmpty()
                    && (fluid.getType() == Fluids.LAVA || fluid.getType() == Fluids.FLOWING_LAVA);
        }

        private net.minecraft.world.level.NoiseColumn column(int x, int z) {
            long key = (((long) x) << 32) | (z & 0xFFFFFFFFL);
            net.minecraft.world.level.NoiseColumn cached = columns.get(key);
            if (cached != null) {
                return cached;
            }
            if (built >= MAX_COLUMNS) {
                return null;
            }
            net.minecraft.world.level.NoiseColumn fresh =
                    generator.getBaseColumn(x, z, live, randomState);
            if (fresh == null) {
                return null;
            }
            built++;
            columns.put(key, fresh);
            return fresh;
        }
    }
}
