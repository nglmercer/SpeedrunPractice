package com.gregor0410.speedrunpractice.adapter121.live;

import com.google.common.collect.ImmutableList;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
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
import net.minecraft.structure.StructureStart;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.collection.BoundedRegionArray;
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
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.AbstractChunkHolder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkGenerationStep;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.GenerationDependencies;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.tick.QueryableTickScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Stage-B lava verification for seed search (plan section 7): runs the
 * game's own decoration pipeline for a candidate seed over noise-stage
 * terrain columns, on search worker threads. Yarn port of the verified
 * 26.3 verifier ({@code StageBLava263}); every vanilla call below was
 * checked against the 1.21.1 decoration loop
 * ({@code ChunkGenerator.generateFeatures} plus
 * {@code PlacedFeatureIndexer.collectIndexedFeatures}, CFR-decompiled from
 * the mapped jar).
 *
 * <p>What is exact: the whole decoration pipeline — every placed
 * feature of every decoration step ({@link PlacedFeatureIndexer} step
 * order, feature order and indices), the decoration seeding
 * ({@code setPopulationSeed}/{@code setDecoratorSeed}), every placement
 * modifier (including the biome filter, which reads candidate-seeded
 * biomes), and every feature body itself (ores, disks, lakes, springs,
 * trees) — all real game code with the candidate seed, in live order,
 * so each feature sees the same blocks live generation would have
 * left. A springs-only replay misfired where an ore blob covered the
 * spring site, missed lava lakes entirely, and gated against a
 * treeless surface the live run did not have (headless probes
 * 2026-09-19); replaying the full pipeline fixes all three.
 *
 * <p>What is nearly exact: the terrain substrate is per-chunk scratch
 * state run through the game's own {@code populateNoise} (noise fill
 * plus aquifers), {@code buildSurface} (surface rules) and {@code carve}
 * (AIR carvers, the only step vanilla runs) with the candidate seed, in
 * the same order live generation uses. Features therefore see the same
 * dirt cover, aquifer water and caves the live run would leave.
 *
 * <p>What is approximated: structure terrain adaptation (the scratch
 * substrate knows no structures) and the LIQUID carver step (vanilla
 * never runs it either). A structure intersecting a lava site can
 * theoretically flip a result either way, which in-game spot checks of
 * found seeds should confirm. A feature the scratch level cannot serve
 * (chunk access the replay does not model) fails soft per feature and
 * is counted in the log, so one unsupported feature can never sink the
 * whole seed.
 *
 * <p>Cost: a full scan builds up to 144 noise chunks and replays every
 * decoration feature inside them, so lava-only searches over wide
 * ranges are slow; combine lava with structure or biome filters so
 * Stage-B runs only for Stage-A survivors.
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
     * Safety valve on scratch-chunk builds per verification. The replay
     * touches the 9x9 scan plus a one-chunk halo (spring tries at chunk
     * borders read neighbours across), so the budget must cover 11x11
     * with slack: an exhausted budget reads missing chunks as air,
     * forging phantom spring holes at the scan edge.
     */
    private static final int MAX_CHUNKS = 144;
    /**
     * Synthetic generation step for the scratch regions. The replay's
     * region overrides serve chunks directly, so the step is only ever
     * read by {@code isChunkLoaded} (every queried chunk reports
     * loaded) and crash details; block writes never go through it.
     */
    private static final ChunkGenerationStep STEP = new ChunkGenerationStep(ChunkStatus.SURFACE,
            new GenerationDependencies(ImmutableList.copyOf(
                    Collections.nCopies(9, ChunkStatus.SURFACE))),
            new GenerationDependencies(ImmutableList.copyOf(
                    Collections.nCopies(9, ChunkStatus.SURFACE))),
            8, (context, step, array, chunk) -> CompletableFuture.completedFuture(chunk));
    /**
     * How far below the local surface a spring source may sit and still
     * count as surface lava (spring sources embed in rock with one
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
        // Every placed feature of every decoration step, in vanilla
        // order: pre-spring features (ores, disks, lakes) shape spring
        // validity, springs and lakes write the lava, and post-spring
        // features (trees) shape the surface gate. Skipping any of them
        // forged both false positives and false negatives.
        List<StepTarget> replay = new ArrayList<StepTarget>();
        for (int step = 0; step < perStep.size(); step++) {
            PlacedFeatureIndexer.IndexedFeatures data = perStep.get(step);
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
        // TEMP probe diagnostics: revert after verification.
        SpeedrunLogger.warn("PROBELAVA121-DIAG steps=" + perStep.size() + " features="
                + replay.size());
        Registry<Biome> biomeRegistry;
        try {
            biomeRegistry = registries.get(RegistryKeys.BIOME);
        } catch (RuntimeException missing) {
            return false;
        }
        if (biomeRegistry == null) {
            return false;
        }
        StructureAccessor structures;
        BiomeAccess access;
        try {
            structures = new StructureAccessor(overworld,
                    server.getSaveProperties().getGeneratorOptions(), null) {
                @Override
                public List<StructureStart> getStructureStarts(ChunkPos pos,
                        Predicate<Structure> predicate) {
                    // No structures exist in the scratch substrate, so
                    // structure terrain adaptation never applies.
                    return Collections.emptyList();
                }

                @Override
                public List<StructureStart> getStructureStarts(ChunkSectionPos sectionPos,
                        Structure structure) {
                    return Collections.emptyList();
                }
            };
            access = overworld.getBiomeAccess().withSource(
                    (x, y, z) -> biomeSource.getBiome(x, y, z, sampler));
        } catch (RuntimeException badSubstrate) {
            return false;
        }
        ColumnLevel level = new ColumnLevel(server, overworld,
                (NoiseChunkGenerator) generator, noiseConfig, biomeSource, sampler, spawn, seed,
                biomeRegistry, structures, access);
        ChunkRandom random =
                new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
        int minChunkX = ChunkSectionPos.getSectionCoord(spawn.getX() - RADIUS - MARGIN_BLOCKS);
        int maxChunkX = ChunkSectionPos.getSectionCoord(spawn.getX() + RADIUS + MARGIN_BLOCKS);
        int minChunkZ = ChunkSectionPos.getSectionCoord(spawn.getZ() - RADIUS - MARGIN_BLOCKS);
        int maxChunkZ = ChunkSectionPos.getSectionCoord(spawn.getZ() + RADIUS + MARGIN_BLOCKS);
        try {
            int failed = 0;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    BlockPos origin = ChunkSectionPos.from(new ChunkPos(chunkX, chunkZ),
                            level.getBottomSectionCoord()).getMinPos();
                    long populationSeed =
                            random.setPopulationSeed(seed, origin.getX(), origin.getZ());
                    for (StepTarget target : replay) {
                        random.setDecoratorSeed(populationSeed, target.index, target.step);
                        level.noteTarget(chunkX, chunkZ, target.step, target.index);
                        try {
                            target.placed.generate(level, generator, random, origin);
                        } catch (RuntimeException unsupported) {
                            // One unservable feature (chunk access the
                            // scratch level does not model) must not sink
                            // the seed; its absence can only cost a find.
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
            // The surface gate runs here, on the finished replay: later
            // steps (trees) raise the surface above early lava, so gating
            // at write time forges false positives (seed 12: an oak
            // branch 14 blocks above a lava pocket).
            if (level.foundLava()) {
                // TEMP probe diagnostics: revert after verification.
                BlockPos first = level.firstLava();
                SpeedrunLogger.warn("PROBELAVA121-DIAG seed=" + seed + " lavaAt="
                        + first + " noiseSurf=" + level.surfaceAt(first)
                        + " how=" + level.firstLavaHow() + " failed=" + failed);
            }
            // TEMP round-5: final replay column for the seed-12 fix check.
            if (seed == 12L) {
                for (int y = 55; y <= 90; y++) {
                    BlockPos at = new BlockPos(-24, y, 23);
                    SpeedrunLogger.warn("PROBELAVA121-RCOL seed=12 at=-24," + y + ",23"
                            + " block=" + level.getBlockState(at).getBlock());
                }
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
     * under construction plus empty shells for the carver's 17x17
     * neighbourhood (carvers only read their generation settings, which
     * key off the candidate-seed biome). Seed, biomes and blending all
     * answer for the candidate world, never the live one.
     */
    private static final class FakeRegion extends ChunkRegion {
        private final ProtoChunk center;
        private final ServerWorld live;
        private final Registry<Biome> biomes;
        private final BiomeAccess access;
        private final long seed;
        private final Map<Long, ProtoChunk> shells = new HashMap<Long, ProtoChunk>();

        private FakeRegion(ServerWorld live, ProtoChunk center, Registry<Biome> biomes,
                BiomeAccess access, long seed) {
            super(live, BoundedRegionArray.<AbstractChunkHolder>create(
                    center.getPos().x, center.getPos().z, 0, (x, z) -> null), STEP, center);
            this.center = center;
            this.live = live;
            this.biomes = biomes;
            this.access = access;
            this.seed = seed;
        }

        @Override
        public Chunk getChunk(int chunkX, int chunkZ) {
            if (chunkX == center.getPos().x && chunkZ == center.getPos().z) {
                return center;
            }
            long key = (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
            ProtoChunk shell = shells.get(key);
            if (shell == null) {
                shell = new ProtoChunk(new ChunkPos(chunkX, chunkZ),
                        UpgradeData.NO_UPGRADE_DATA, live, biomes, null);
                shells.put(key, shell);
            }
            return shell;
        }

        @Override
        public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
            return getChunk(chunkX, chunkZ);
        }

        @Override
        public BiomeAccess getBiomeAccess() {
            return access;
        }

        @Override
        public long getSeed() {
            return seed;
        }

        @Override
        public boolean needsBlending(ChunkPos chunkPos, int checkRadius) {
            // Scratch chunks model a fresh candidate world, which never
            // borders old-version terrain.
            return false;
        }
    }

    /**
     * A {@link StructureWorldAccess} over lazily built surfaced and
     * carved scratch chunks for the candidate seed. Reads serve the
     * placement pipeline; writes are recorded (lava writes are the
     * verification signal) and visible to later reads. Everything the
     * spring path never touches delegates to the live level or answers
     * degenerately.
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
        private final Registry<Biome> biomeRegistry;
        private final StructureAccessor structures;
        private final BiomeAccess access;
        private final Map<Long, ProtoChunk> chunks = new HashMap<Long, ProtoChunk>();
        private final Map<BlockPos, BlockState> overrides = new HashMap<BlockPos, BlockState>();
        private final List<BlockPos> lava = new ArrayList<BlockPos>();
        private int built;
        private ProtoChunk thrashChunk;

        private ColumnLevel(MinecraftServer server, ServerWorld live,
                NoiseChunkGenerator generator, NoiseConfig noiseConfig, BiomeSource biomeSource,
                MultiNoiseUtil.MultiNoiseSampler sampler, BlockPos spawn, long seed,
                Registry<Biome> biomeRegistry, StructureAccessor structures, BiomeAccess access) {
            this.server = server;
            this.live = live;
            this.generator = generator;
            this.noiseConfig = noiseConfig;
            this.biomeSource = biomeSource;
            this.sampler = sampler;
            this.spawn = spawn;
            this.seed = seed;
            this.biomeRegistry = biomeRegistry;
            this.structures = structures;
            this.access = access;
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

        /**
         * Lowest Y that still counts as surface lava at a column: the
         * local surface minus {@value #SURFACE_MARGIN}. Fails closed
         * (unreadable surface verifies nothing).
         */
        private int surfaceFloor(int x, int z) {
            try {
                return getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - SURFACE_MARGIN;
            } catch (RuntimeException unreadable) {
                return Integer.MAX_VALUE;
            }
        }

        // TEMP probe diagnostics: revert after verification.
        BlockPos firstLava() {
            for (int i = 0; i < lava.size(); i++) {
                BlockPos at = lava.get(i);
                if (at.getY() >= surfaceFloor(at.getX(), at.getZ())) {
                    return at;
                }
            }
            return null;
        }

        // TEMP probe diagnostics: revert after verification.
        String firstLavaHow() {
            for (int i = 0; i < lava.size(); i++) {
                BlockPos at = lava.get(i);
                if (at.getY() >= surfaceFloor(at.getX(), at.getZ())) {
                    return i < firstLavaHows.size() ? firstLavaHows.get(i) : "?";
                }
            }
            return "?";
        }

        /** Placing chunk/step/index of the feature currently generating. */
        private String currentHow = "?";
        /** Parallel to {@link #lava}: where each candidate came from. */
        private final List<String> firstLavaHows = new ArrayList<String>();
        private boolean budgetTripped;

        void noteTarget(int chunkX, int chunkZ, int step, int index) {
            currentHow = chunkX + "," + chunkZ + "/s" + step + "i" + index;
        }

        // TEMP probe diagnostics: revert after verification.
        int surfaceAt(BlockPos at) {
            if (at == null) {
                return Integer.MIN_VALUE;
            }
            try {
                return getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            } catch (RuntimeException unreadable) {
                return Integer.MIN_VALUE;
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
            if (pos.getY() < getBottomY() || pos.getY() >= getTopY()) {
                return Blocks.AIR.getDefaultState();
            }
            ProtoChunk chunk = surfaced(
                    ChunkSectionPos.getSectionCoord(pos.getX()),
                    ChunkSectionPos.getSectionCoord(pos.getZ()));
            if (chunk == null) {
                return Blocks.AIR.getDefaultState();
            }
            try {
                BlockState state = chunk.getBlockState(pos);
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
            if (isLava(state) && withinRadius(pos)) {
                lava.add(pos.toImmutable());
                firstLavaHows.add(currentHow);
                // TEMP substrate dump: revert after verification.
                if (lava.size() == 1) {
                    dumpSubstrate(pos.toImmutable());
                }
            }
            return true;
        }

        // TEMP substrate dump: revert after verification.
        private void dumpSubstrate(BlockPos at) {
            StringBuilder out = new StringBuilder();
            out.append("PROBELAVA121-SUB seed=").append(seed).append(" at=").append(at)
                    .append(" built=").append(built);
            int[][] offsets = {{0, 0, 0}, {0, 1, 0}, {0, -1, 0}, {-1, 0, 0}, {1, 0, 0},
                {0, 0, -1}, {0, 0, 1}};
            for (int[] o : offsets) {
                int x = at.getX() + o[0];
                int y = at.getY() + o[1];
                int z = at.getZ() + o[2];
                out.append(" [").append(o[0]).append(',').append(o[1]).append(',')
                        .append(o[2]).append('=');
                try {
                    long key = (((long) ChunkSectionPos.getSectionCoord(x)) << 32)
                            | (ChunkSectionPos.getSectionCoord(z) & 0xFFFFFFFFL);
                    ProtoChunk chunk = chunks.get(key);
                    BlockState state = chunk == null ? null
                            : chunk.getBlockState(new BlockPos(x, y, z));
                    if (state == null) {
                        out.append('?');
                    } else {
                        String name = state.getBlock().getTranslationKey();
                        int dot = name.lastIndexOf('.');
                        out.append(dot >= 0 ? name.substring(dot + 1) : name);
                        if (!state.getFluidState().isEmpty()) {
                            out.append('+').append(state.getFluidState().getFluid() == Fluids.WATER
                                    || state.getFluidState().getFluid() == Fluids.FLOWING_WATER
                                    ? "W" : "F");
                        }
                    }
                } catch (RuntimeException unreadable) {
                    out.append('?');
                }
                out.append(']');
            }
            SpeedrunLogger.warn(out.toString());
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
            // Ore veins (and any other section-cache user) read and write
            // through a section cache over this call; it must serve the
            // scratch chunk, never throw. Writes land directly in the
            // ProtoChunk where block reads see them on override-miss,
            // while override writes keep shadowing chunk state exactly
            // as live chunk writes would — both views converge (same
            // shape as the 26.3 BulkSectionAccess find, 2026-09-19).
            ProtoChunk chunk = surfaced(chunkX, chunkZ);
            if (chunk != null) {
                return chunk;
            }
            return thrash();
        }

        @Override
        public Chunk getChunk(BlockPos pos) {
            // Vanilla's placement helper marks the non-air blocks above
            // every write for post-processing through this call, and so
            // does the multiface-growth feature; both are the only call
            // sites in the whole decoration pipeline (same shape as the
            // 26.3 javap audit). The replay never runs post-processing,
            // so the marks are inert — but the call must not throw, or
            // every helper-using feature (all ores included, ~20 skipped
            // placements per chunk) is silently dropped from the replay.
            ProtoChunk chunk = surfaced(ChunkSectionPos.getSectionCoord(pos.getX()),
                    ChunkSectionPos.getSectionCoord(pos.getZ()));
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
                thrashChunk = new ProtoChunk(new ChunkPos(0, 0),
                        UpgradeData.NO_UPGRADE_DATA, live, biomeRegistry, null);
            }
            return thrashChunk;
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            // Dungeons place a spawner and configure its block entity;
            // a throwaway keeps vanilla quiet. Nothing reads it back
            // for block placement, so lava is unaffected.
            BlockState state = getBlockState(pos);
            if (state.isOf(Blocks.SPAWNER)) {
                return new MobSpawnerBlockEntity(pos, state);
            }
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

        /**
         * The fully generated candidate-seed chunk at {@code (chunkX,
         * chunkZ)}: noise fill plus aquifers, surface rules and AIR
         * carvers, each the game's own stage in live order, then cached.
         * Every count in the verifier reads through here, so springs see
         * the same blocks live generation would leave.
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
                fresh = new ProtoChunk(new ChunkPos(chunkX, chunkZ),
                        UpgradeData.NO_UPGRADE_DATA, live, biomeRegistry, null);
                generator.populateNoise(Blender.getNoBlending(), noiseConfig, structures, fresh)
                        .join();
                FakeRegion region = new FakeRegion(live, fresh, biomeRegistry, access, seed);
                generator.buildSurface(region, structures, noiseConfig, fresh);
                Blender.createCarvingMasks(region, fresh);
                generator.carve(region, seed, noiseConfig, access, structures, fresh,
                        GenerationStep.Carver.AIR);
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
