package com.gregor0410.speedrunpractice.adapter263.live;

import com.google.common.collect.ImmutableList;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyStructureManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Stage-B lava verification for seed search (plan section 7): runs the
 * game's own decoration pipeline for a candidate seed over noise-stage
 * terrain columns, on search worker threads.
 *
 * <p>What is exact: the whole decoration pipeline — every placed
 * feature of every decoration step ({@link FeatureSorter} step order,
 * feature order and indices), the decoration seeding
 * ({@code setDecorationSeed} and {@code setFeatureSeed}), every
 * placement modifier, the biome checks (candidate-seeded climate
 * sampler), and every feature body itself (ores, disks, lakes,
 * springs, trees) — all real game code with the candidate seed, in
 * live order, so each feature sees the same blocks live generation
 * would have left. A springs-only replay misfired where a copper blob
 * covered the spring site, missed lava lakes entirely, and gated
 * against a treeless surface the live run did not have (headless
 * probes 2026-09-19); replaying the full pipeline fixes all three.
 *
 * <p>What is nearly exact: the terrain substrate is per-chunk scratch
 * state run through the game's own {@code createBiomes} and
 * {@code buildTerrain} (biome fill, noise fill plus aquifers, surface
 * rules and carvers) with the candidate seed, in the same order live
 * generation uses. Features therefore see the same dirt cover, aquifer
 * water and caves the live run would leave.
 *
 * <p>What is approximated: structure pieces and structure terrain
 * adaptation (the scratch substrate knows no structures). A structure
 * intersecting a lava site can theoretically flip a result either way,
 * which in-game spot checks of found seeds should confirm. A feature
 * the scratch level cannot serve (chunk access the replay does not
 * model) fails soft per feature and is counted in the log, so one
 * unsupported feature can never sink the whole seed.
 *
 * <p>Cost: a full scan builds up to 144 terrain chunks and replays
 * every decoration feature inside them, so lava-only searches over
 * wide ranges are slow; combine lava with structure or biome filters
 * so Stage-B runs only for Stage-A survivors.
 *
 * <p>Only surface lava counts: a placement verifies the seed only when
 * it sits no deeper than {@value #SURFACE_MARGIN} blocks below the
 * local surface. Deep cave springs the player would never find
 * must not satisfy a "surface lava near spawn" query (headless probe
 * 2026-09-19: ungated Stage-B fired on y=-50..-5 springs for seeds
 * with zero surface lava).
 *
 * <p>Only reports lava it actually places: any failure or budget
 * exhaustion verifies nothing and the seed mismatches (false negatives
 * only, never false positives from the verifier itself).
 */
final class StageBLava263 {
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
     * Safety valve on scratch-chunk builds per verification. The replay
     * touches the 9x9 scan plus a one-chunk halo (spring tries at chunk
     * borders read neighbours across), so the budget must cover 11x11
     * with slack: an exhausted budget reads missing chunks as air,
     * forging phantom spring holes at the scan edge.
     */
    private static final int MAX_CHUNKS = 144;
    /**
     * Synthetic generation step for the scratch regions. The dependency
     * lists stay empty on purpose: the region constructor eagerly maps
     * its chunk cache through them, and any entry would dereference the
     * placeholder slots. The replay's region overrides serve chunks
     * directly, so the step is only ever read by crash details
     * afterwards; block writes never go through it.
     */
    private static final ChunkStep STEP = new ChunkStep(ChunkStatus.TERRAIN,
            new ChunkDependencies(ImmutableList.of()),
            new ChunkDependencies(ImmutableList.of()),
            8, (context, step, array, chunk) -> CompletableFuture.completedFuture(chunk));
    /**
     * How far below the local surface a spring source may sit and still
     * count as surface lava (spring sources embed in rock with one
     * escape face, and lava flows a few blocks from the source).
     */
    private static final int SURFACE_MARGIN = 8;

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
        // Every placed feature of every decoration step, in vanilla
        // order: pre-spring features (ores, disks, lakes) shape spring
        // validity, springs and lakes write the lava, and post-spring
        // features (trees) shape the surface gate. Skipping any of them
        // forged both false positives and false negatives.
        List<StepTarget> replay = new ArrayList<StepTarget>();
        for (int step = 0; step < perStep.size(); step++) {
            FeatureSorter.StepFeatureData data = perStep.get(step);
            if (data == null || data.features() == null) {
                continue;
            }
            for (PlacedFeature feature : data.features()) {
                try {
                    replay.add(new StepTarget(feature,
                            data.indexMapping().applyAsInt(feature), step));
                } catch (RuntimeException unindexed) {
                    // Live would seed this feature too, but the index
                    // map is the only source of indices; fail soft.
                }
            }
        }
        if (replay.isEmpty()) {
            return false;
        }
        BiomeManager manager;
        PalettedContainerFactory containers;
        try {
            manager = overworld.getBiomeManager().withDifferentSource(resolver);
            containers = PalettedContainerFactory.create(server.registryAccess());
        } catch (RuntimeException badSubstrate) {
            return false;
        }
        ColumnLevel level = new ColumnLevel(server, overworld,
                (NoiseBasedChunkGenerator) generator, randomState, resolver, seed, spawn,
                manager, containers);
        FeaturePlacer placer = new FeaturePlacer(level, generator);
        WorldgenRandom random =
                new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        int minChunkX = SectionPos.blockToSectionCoord(spawn.getX() - RADIUS - MARGIN_BLOCKS);
        int maxChunkX = SectionPos.blockToSectionCoord(spawn.getX() + RADIUS + MARGIN_BLOCKS);
        int minChunkZ = SectionPos.blockToSectionCoord(spawn.getZ() - RADIUS - MARGIN_BLOCKS);
        int maxChunkZ = SectionPos.blockToSectionCoord(spawn.getZ() + RADIUS + MARGIN_BLOCKS);
        try {
            int failed = 0;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    BlockPos origin = SectionPos.of(new ChunkPos(chunkX, chunkZ),
                            level.getMinSectionY()).origin();
                    long decorationSeed = random.setDecorationSeed(seed, origin.getX(), origin.getZ());
                    for (StepTarget target : replay) {
                        random.setFeatureSeed(decorationSeed, target.index, target.step);
                        try {
                            placer.placeWithBiomeCheck(target.placed, random, origin);
                        } catch (RuntimeException unsupported) {
                            // One unservable feature must not sink the
                            // seed; its absence can only cost a find.
                            failed++;
                            continue;
                        }
                    }
                }
            }
            if (failed > 0) {
                SpeedrunLogger.warn("Stage-B lava check for seed " + seed + " skipped "
                        + failed + " unservable feature placements");
            }
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Stage-B lava check failed for seed " + seed + ": " + failure);
            return false;
        }
        return level.foundLava();
    }

    /** One replayed decoration feature: step plus vanilla feature index. */
    private static final class StepTarget {
        final PlacedFeature placed;
        final int index;
        final int step;

        private StepTarget(PlacedFeature placed, int index, int step) {
            this.placed = placed;
            this.index = index;
            this.step = step;
        }
    }

    /**
     * Minimal region serving the replay's substrate stages: the chunk
     * under construction plus biome-filled shells for the carver's 17x17
     * neighbourhood (carvers only read their generation settings, which
     * key off the candidate-seed biome, and the surface pass reads the
     * 3x3 biome palettes). Seed, biomes and blending all answer for the
     * candidate world, never the live one.
     */
    private static final class FakeRegion extends WorldGenRegion {
        private final ProtoChunk center;
        private final ServerLevel live;
        private final NoiseBasedChunkGenerator generator;
        private final RandomState randomState;
        private final PalettedContainerFactory containers;
        private final BiomeManager manager;
        private final Map<Long, ProtoChunk> shells;
        private final long seed;

        private FakeRegion(ServerLevel live, NoiseBasedChunkGenerator generator,
                RandomState randomState, ProtoChunk center,
                PalettedContainerFactory containers, BiomeManager manager,
                Map<Long, ProtoChunk> shells, long seed) {
            super(live, StaticCache2D.<GenerationChunkHolder>create(
                    center.getPos().x(), center.getPos().z(), 0, (x, z) -> null), STEP, center);
            this.live = live;
            this.generator = generator;
            this.randomState = randomState;
            this.center = center;
            this.containers = containers;
            this.manager = manager;
            this.shells = shells;
            this.seed = seed;
        }

        @Override
        public ChunkAccess getChunk(int chunkX, int chunkZ) {
            if (chunkX == center.getPos().x() && chunkZ == center.getPos().z()) {
                return center;
            }
            long key = (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
            ProtoChunk shell = shells.get(key);
            if (shell == null) {
                shell = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, live,
                        containers, null);
                generator.createBiomes(randomState, Blender.empty(),
                        EmptyStructureManager.INSTANCE, shell).join();
                shells.put(key, shell);
            }
            return shell;
        }

        @Override
        public BiomeManager getBiomeManager() {
            // The attribute system captures this during super(), before
            // the candidate manager is assigned; the live manager keeps
            // construction safe and is never consulted for candidate
            // answers afterwards.
            BiomeManager ready = manager;
            return ready != null ? ready : getLevel().getBiomeManager();
        }

        @Override
        public boolean hasChunk(int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public long getSeed() {
            return seed;
        }

        @Override
        public boolean isOldChunkAround(ChunkPos pos, int range) {
            // Scratch chunks model a fresh candidate world, which never
            // borders old-version terrain.
            return false;
        }
    }

    /**
     * A {@link WorldGenLevel} over lazily built terrain scratch chunks
     * for the candidate seed. Reads serve the placement pipeline; writes
     * are recorded (lava writes are the verification signal) and visible
     * to later reads. Everything the spring path never touches delegates
     * to the live level or answers degenerately.
     */
    private static final class ColumnLevel implements WorldGenLevel {
        private final MinecraftServer server;
        private final ServerLevel live;
        private final NoiseBasedChunkGenerator generator;
        private final RandomState randomState;
        private final BiomeResolver resolver;
        private final BlockPos spawn;
        private final long seed;
        private final BiomeManager manager;
        private final PalettedContainerFactory containers;
        private final Map<Long, ProtoChunk> chunks = new HashMap<Long, ProtoChunk>();
        private final Map<Long, ProtoChunk> shells = new HashMap<Long, ProtoChunk>();
        private final Map<BlockPos, BlockState> overrides = new HashMap<BlockPos, BlockState>();
        private final List<BlockPos> lava = new ArrayList<BlockPos>();
        private int built;
        private ProtoChunk thrashChunk;

        private ColumnLevel(MinecraftServer server, ServerLevel live,
                NoiseBasedChunkGenerator generator, RandomState randomState,
                BiomeResolver resolver, long seed, BlockPos spawn,
                BiomeManager manager, PalettedContainerFactory containers) {
            this.server = server;
            this.live = live;
            this.generator = generator;
            this.randomState = randomState;
            this.resolver = resolver;
            this.spawn = spawn;
            this.seed = seed;
            this.manager = manager;
            this.containers = containers;
        }

        /**
         * Whether any recorded lava counts against the finished replay's
         * surface. Recording keeps every in-radius lava write; the
         * surface gate runs here because later steps (trees) raise the
         * surface above early lava. Only call once the replay is
         * complete: mid-replay gating forges false positives.
         */
        boolean foundLava() {
            return !budgetTripped && firstLava() != null;
        }

        BlockPos firstLava() {
            for (int i = 0; i < lava.size(); i++) {
                BlockPos at = lava.get(i);
                if (at.getY() >= surfaceFloor(at.getX(), at.getZ())) {
                    return at;
                }
            }
            return null;
        }

        private boolean budgetTripped;

        /**
         * Lowest Y that still counts as surface lava at a column: the
         * local surface minus {@value #SURFACE_MARGIN}. Fails closed
         * (unreadable surface verifies nothing).
         */
        private int surfaceFloor(int x, int z) {
            try {
                return getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - SURFACE_MARGIN;
            } catch (RuntimeException unreadable) {
                return Integer.MAX_VALUE;
            }
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
            ProtoChunk chunk = surfaced(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            if (chunk == null) {
                return Blocks.AIR.defaultBlockState();
            }
            try {
                BlockState state = chunk.getBlockState(pos);
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
            if (isLava(state) && withinRadius(pos)) {
                lava.add(pos.immutable());
            }
            return true;
        }

        private boolean withinRadius(BlockPos at) {
            long dx = (long) at.getX() - spawn.getX();
            long dz = (long) at.getZ() - spawn.getZ();
            return dx * dx + dz * dz <= (long) RADIUS * RADIUS;
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
            // Ore veins (and any other section-cache user) read and write
            // through a BulkSectionAccess over this call; it must serve the
            // scratch chunk, never throw. Writes land directly in the
            // ProtoChunk where block reads see them on override-miss, while
            // override writes keep shadowing chunk state exactly as live
            // chunk writes would — both views converge (headless probe
            // 2026-09-19: throwing here skipped ~1900 placements per seed).
            ProtoChunk chunk = surfaced(chunkX, chunkZ);
            if (chunk != null) {
                return chunk;
            }
            return thrash();
        }

        @Override
        public ChunkAccess getChunk(BlockPos pos) {
            // Vanilla's placement helper marks the non-air blocks above
            // every write for post-processing through this call, and so
            // does the multiface-growth feature; both are the only call
            // sites in the whole decoration pipeline (javap audit over
            // all 112 feature/placement classes, 2026-09-19). The replay
            // never runs post-processing, so the marks are inert — but
            // the call must not throw, or every helper-using feature
            // (all ores included, ~25 skipped placements per chunk) is
            // silently dropped from the replay.
            ProtoChunk chunk = surfaced(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            if (chunk != null) {
                return chunk;
            }
            return thrash();
        }

        /**
         * Inert chunk absorbing post-processing marks past the build
         * budget. Block reads never route through here (they serve air
         * directly), so its empty state is never observed.
         */
        private ProtoChunk thrash() {
            if (thrashChunk == null) {
                thrashChunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, live,
                        containers, null);
            }
            return thrashChunk;
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
            // Dungeons place a spawner and configure its block entity;
            // a throwaway keeps vanilla quiet. Nothing reads it back
            // for block placement, so lava is unaffected.
            BlockState state = getBlockState(pos);
            if (state.is(Blocks.SPAWNER)) {
                return new net.minecraft.world.level.block.entity.SpawnerBlockEntity(pos, state);
            }
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
            if (type == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES) {
                // Vanilla keys this map off its own tag (leaves excluded),
                // not the motion-blocking tag.
                return state.is(net.minecraft.tags.BlockTags
                                .BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES)
                        || !state.getFluidState().isEmpty();
            }
            if (state.is(net.minecraft.tags.BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP)) {
                return true;
            }
            return type == Heightmap.Types.MOTION_BLOCKING
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

        /**
         * The fully generated candidate-seed chunk at {@code (chunkX,
         * chunkZ)}: biome fill plus the game's own terrain stage (noise
         * fill with aquifers, surface rules and carvers) in live order,
         * then cached. Every count in the verifier reads through here,
         * so springs see the same blocks live generation would leave.
         */
        private ProtoChunk surfaced(int chunkX, int chunkZ) {
            long key = (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
            ProtoChunk cached = chunks.get(key);
            if (cached != null) {
                return cached;
            }
            if (built >= MAX_CHUNKS) {
                budgetTripped = true;
                return null;
            }
            ProtoChunk fresh;
            try {
                fresh = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, live,
                        containers, null);
                generator.createBiomes(randomState, Blender.empty(),
                        EmptyStructureManager.INSTANCE, fresh).join();
                FakeRegion region = new FakeRegion(live, generator, randomState, fresh,
                        containers, manager, shells, seed);
                Set<Holder<Biome>> possibleBiomes = new HashSet<Holder<Biome>>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        region.getChunk(chunkX + dx, chunkZ + dz)
                                .collectBiomesInPalette(possibleBiomes);
                    }
                }
                generator.buildTerrain(fresh, Blender.of(region), randomState,
                        EmptyStructureManager.INSTANCE, manager, region, possibleBiomes).join();
            } catch (RuntimeException badChunk) {
                return null;
            }
            if (fresh == null) {
                return null;
            }
            built++;
            chunks.put(key, fresh);
            return fresh;
        }
    }
}
