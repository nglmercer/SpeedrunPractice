package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedFilters;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Seed analyzer for 26.3 (plan section 18). Unlike the 1.16.1 transcription,
 * nearly everything here is a direct call into public worldgen API with a
 * per-seed {@link RandomState}:
 *
 * <ul>
 *   <li>Spawn: the generator origin plus the game spawn height
 *       ({@code setInitialSpawn} without its chunk-loading 11x11 refinement,
 *       so predictions can sit up to ~80 blocks from the refined spawn;
 *       distances are measured from the prediction).</li>
 *   <li>Region structures: the game's own locate walk (rings expand
 *       {@code 0..bound}, border cells in {@code dx}/{@code dz} order,
 *       first verifying candidate wins â€” never nearest) plus the game's
 *       placement, restriction and generation checks
 *       ({@code isStructureChunk},
 *       {@code applyAdditionalChunkRestrictions} and {@code generate}),
 *       including multi-entry set selection replicated from
 *       {@code createStructures}. Both replications were verified against
 *       26.3 bytecode.</li>
 *   <li>Strongholds: the exact ring positions from
 *       {@code getRingPositionsFor}, with ring numbers from the replicated
 *       ring-growth rule.</li>
 * </ul>
 *
 * <p>Stage-B verification runs for lava-constrained queries after Stage-A
 * passes: the game's own lava-spring placement pipeline executes for the
 * candidate seed over noise-stage terrain columns
 * ({@link StageBLava263}), so {@code lava.available} reflects real
 * worldgen. Bastion subtypes come from the generated start's template,
 * the same reading live lookups use.
 *
 * <p>Preset ids resolve tag-first: family ids like {@code village} name a
 * structure tag on modern versions (there is no {@code minecraft:village}
 * structure, only the five biome variants plus the tag), so each member is
 * searched and the nearest first-hit wins. Ids without a tag resolve
 * directly.
 *
 * <p>{@code location.<id>} findings carry {@code getLocatePos} X/Z (exactly
 * what {@code /locate} reports); Y is conventional (overworld sea level,
 * 64 in the Nether/End), not a prediction.
 *
 * <p>Threading: every call builds fresh random/structure state, so
 * concurrent search workers share nothing mutable. Live registries,
 * generators, sources and template managers are only read. Calls need a
 * loaded server (any single-player world) for those live objects.
 */
public final class SeedAnalyzer263 implements SeedAnalyzer {
    /** Region-ring cap around the search center (same bound as 1.16.1). */
    private static final int MAX_RINGS = 64;
    /** Candidate-chunk cap per structure per seed. */
    private static final int RECENT_CAP = 4096;
    /** End search center: the main island, like 1.16.1. */
    private static final BlockPos END_CENTER = new BlockPos(100, 49, 0);

    private final LiveAdapter263 live;

    public SeedAnalyzer263(LiveAdapter263 live) {
        if (live == null) {
            throw new IllegalArgumentException("live adapter must not be null");
        }
        this.live = live;
    }

    @Override
    public SeedAnalysis analyze(long seed, SeedQuery query) {
        if (query == null) {
            return SeedAnalysis.mismatch();
        }
        try {
            return analyzeLive(seed, query);
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Seed analysis failed for " + seed + ": " + failure);
            return SeedAnalysis.mismatch();
        }
    }

    @Override
    public boolean matches(long seed, SeedQuery query) {
        return analyze(seed, query).matches();
    }

    @Override
    public boolean supportsLava() {
        return true;
    }

    private SeedAnalysis analyzeLive(long seed, SeedQuery query) {
        MinecraftServer server = live.server();
        if (server == null) {
            return SeedAnalysis.mismatch();
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return SeedAnalysis.mismatch();
        }
        RegistryAccess registries = server.registryAccess();
        HolderGetter<NormalNoise> noises;
        HolderLookup<StructureSet> sets;
        HolderLookup<Structure> structures;
        try {
            noises = registries.lookupOrThrow(Registries.NOISE);
            sets = registries.lookupOrThrow(Registries.STRUCTURE_SET);
            structures = registries.lookupOrThrow(Registries.STRUCTURE);
        } catch (RuntimeException missing) {
            return SeedAnalysis.mismatch();
        }
        StructureTemplateManager templates = server.getStructureTemplateManager();
        Dim over = dim(overworld, seed, noises, sets);
        if (over == null) {
            return SeedAnalysis.mismatch();
        }
        ServerLevel netherLevel = server.getLevel(Level.NETHER);
        ServerLevel endLevel = server.getLevel(Level.END);
        Dim nether = netherLevel == null ? null : dim(netherLevel, seed, noises, sets);
        Dim end = endLevel == null ? null : dim(endLevel, seed, noises, sets);

        Map<String, Object> findings = new LinkedHashMap<String, Object>();
        BlockPos spawn = predictSpawn(over);
        String spawnBiome = biomeId(over.resolver.getNoiseBiome(
                QuartPos.fromBlock(spawn.getX()), QuartPos.fromBlock(spawn.getY()),
                QuartPos.fromBlock(spawn.getZ())));
        if (spawnBiome == null) {
            return SeedAnalysis.mismatch();
        }
        findings.put(SeedFilters.FIND_BIOME_SPAWN, spawnBiome);
        boolean ok = query.requiredBiome() == null
                || FeatureIds263.bare(query.requiredBiome()).equals(FeatureIds263.bare(spawnBiome));
        boolean sawBastion = false;
        boolean sawStronghold = false;

        for (Map.Entry<String, Integer> required : query.requiredStructures().entrySet()) {
            String id = required.getKey();
            int maxDistance = required.getValue() == null ? Integer.MAX_VALUE : required.getValue();
            List<Holder<Structure>> candidates;
            try {
                candidates = resolveCandidates(structures, id);
            } catch (PracticeException | RuntimeException unknown) {
                return SeedAnalysis.mismatch();
            }
            if (candidates.isEmpty()) {
                return SeedAnalysis.mismatch();
            }
            Found best = null;
            Placed bestPlaced = null;
            boolean placementSkipped = false;
            for (Holder<Structure> holder : candidates) {
                Structure structure = holder.value();
                String member = memberId(holder, id);
                Placed placed = locateHome(holder, over, nether, end);
                if (placed == null) {
                    continue;
                }
                BlockPos center = placed.dim == over ? spawn
                        : placed.dim == nether ? new BlockPos(spawn.getX() / 8, 64, spawn.getZ() / 8)
                        : END_CENTER;
                Found found;
                if (placed.placement instanceof ConcentricRingsStructurePlacement) {
                    sawStronghold |= isStronghold(member);
                    found = findStronghold(seed, server, registries, templates, holder, structure,
                            placed, center, maxDistance, query.strongholdRing());
                } else if (placed.placement instanceof RandomSpreadStructurePlacement) {
                    sawBastion |= isBastion(member);
                    String requiredType =
                            isBastion(member) ? query.bastionType() : null;
                    found = findSpread(seed, server, registries, templates, holder, structure,
                            placed, center, maxDistance, requiredType);
                } else {
                    placementSkipped = true;
                    continue;
                }
                if (found != null && found.distance <= maxDistance
                        && (best == null || found.distance < best.distance)) {
                    best = found;
                    bestPlaced = placed;
                }
            }
            if (best == null) {
                if (placementSkipped) {
                    SpeedrunLogger.warn("Seed analysis skips \"" + id
                            + "\": unsupported placement type");
                }
                return SeedAnalysis.mismatch();
            }
            findings.put(SeedFilters.FIND_STRUCTURE_PREFIX + id + SeedFilters.FIND_STRUCTURE_SUFFIX,
                    best.distance);
            findings.put("location." + id, new PracticePosition(
                    best.pos.getX(), locationY(bestPlaced, overworld), best.pos.getZ()));
            if (bestPlaced.placement instanceof ConcentricRingsStructurePlacement && best.ring > 0) {
                findings.put(SeedFilters.FIND_STRONGHOLD_RING, (long) best.ring);
            }
        }
        if (query.bastionType() != null && !sawBastion) {
            return SeedAnalysis.mismatch();
        }
        if (query.strongholdRing() > 0 && !sawStronghold) {
            return SeedAnalysis.mismatch();
        }
        if (query.lavaRequired()) {
            // Stage-B runs only for Stage-A survivors: real spring-placement
            // verification for the candidate seed.
            boolean lava;
            try {
                lava = StageBLava263.verify(server, overworld, over.generator, over.randomState,
                        over.resolver, spawn, seed);
            } catch (RuntimeException failure) {
                SpeedrunLogger.warn("Stage-B lava check failed for seed " + seed + ": " + failure);
                return SeedAnalysis.mismatch();
            }
            if (!lava) {
                return SeedAnalysis.mismatch();
            }
            findings.put(SeedFilters.FIND_LAVA, Boolean.TRUE);
        }
        return SeedAnalysis.of(ok, findings);
    }

    /** Per-dimension reusable live objects plus per-seed random state. */
    private static Dim dim(ServerLevel level, long seed, HolderGetter<NormalNoise> noises,
            HolderLookup<StructureSet> sets) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator)) {
            return null;
        }
        NoiseGeneratorSettings settings =
                ((NoiseBasedChunkGenerator) generator).generatorSettings().value();
        RandomState randomState = RandomState.create(noises, seed, settings);
        return new Dim(level, generator, generator.getBiomeSource(), randomState,
                randomState.createClimateSampler(SamplerContext.builder().enableCaches().build()),
                generator.getBiomeSource().createUncachedResolver(randomState),
                ChunkGeneratorStructureState.createForNormal(
                        randomState, seed, new ChunkPos(0, 0), generator.getBiomeSource(), sets));
    }

    /** Generator origin plus game spawn height (no chunk refinement). */
    private static BlockPos predictSpawn(Dim over) {
        ChunkPos origin = over.generator.getOrigin(over.randomState);
        int y = over.generator.getSpawnHeight(over.level);
        return origin.getWorldPosition().offset(8, y, 8);
    }

    /**
     * Predicted spawn for a seed: the overworld search center every
     * overworld distance is measured from (the Nether center derives as
     * {@code (spawnX/8, 64, spawnZ/8)}; the End center is fixed). Null when
     * no server is loaded. Public so callers can cross-check predictions
     * against live locates from the same centers.
     */
    public BlockPos spawnCenter(long seed) {
        try {
            MinecraftServer server = live.server();
            if (server == null) {
                return null;
            }
            RegistryAccess registries = server.registryAccess();
            HolderGetter<NormalNoise> noises = registries.lookupOrThrow(Registries.NOISE);
            HolderLookup<StructureSet> sets = registries.lookupOrThrow(Registries.STRUCTURE_SET);
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) {
                return null;
            }
            Dim over = dim(overworld, seed, noises, sets);
            return over == null ? null : predictSpawn(over);
        } catch (RuntimeException missing) {
            return null;
        }
    }

    private static double locationY(Placed placed, ServerLevel overworld) {
        if (placed.dim.level.dimension() == Level.OVERWORLD) {
            return (double) overworld.getSeaLevel();
        }
        return 64.0;
    }

    /**
     * Resolves a preset id to the structures it names: the structure tag
     * first (family ids like {@code village}, {@code mineshaft},
     * {@code shipwreck}, {@code ocean_ruin} and {@code ruined_portal} keep
     * their 1.16.1 whole-family meaning this way), otherwise the single
     * structure. Unknown ids resolve to no candidates. Findings still use
     * the preset id verbatim so shared filters keep matching.
     */
    private static List<Holder<Structure>> resolveCandidates(HolderLookup<Structure> structures,
            String id) throws PracticeException {
        java.util.Optional<HolderSet.Named<Structure>> tag = structures.get(FeatureIds263.tagKey(id));
        if (tag.isPresent() && tag.get().size() > 0) {
            List<Holder<Structure>> members = new ArrayList<Holder<Structure>>(tag.get().size());
            for (Holder<Structure> member : tag.get()) {
                members.add(member);
            }
            return members;
        }
        java.util.Optional<Holder.Reference<Structure>> direct = structures.get(FeatureIds263.key(id));
        if (direct.isPresent()) {
            List<Holder<Structure>> single = new ArrayList<Holder<Structure>>(1);
            single.add(direct.get());
            return single;
        }
        return new ArrayList<Holder<Structure>>(0);
    }

    /** Registry id of one resolved member (falls back to the preset id). */
    private static String memberId(Holder<Structure> holder, String presetId) {
        if (holder == null) {
            return presetId;
        }
        return holder.unwrapKey().map(key -> key.identifier().toString()).orElse(presetId);
    }

    /** Finds the dimension whose sets place this structure (vanilla: unique). */
    private static Placed locateHome(Holder<Structure> holder, Dim over, Dim nether, Dim end) {
        Dim[] dims = new Dim[]{over, nether, end};
        for (Dim dim : dims) {
            if (dim == null) {
                continue;
            }
            List<StructurePlacement> placements = dim.state.getPlacementsForStructure(holder);
            if (placements.isEmpty()) {
                continue;
            }
            StructurePlacement placement = placements.get(0);
            Holder<StructureSet> set = owningSet(dim, holder, placement);
            if (set != null) {
                return new Placed(dim, set, placement);
            }
        }
        return null;
    }

    private static Holder<StructureSet> owningSet(Dim dim, Holder<Structure> holder,
            StructurePlacement placement) {
        for (Holder<StructureSet> set : dim.state.possibleStructureSets()) {
            if (set.value().placement() != placement) {
                continue;
            }
            for (StructureSelectionEntry entry : set.value().structures()) {
                if (entry.structure().value() == holder.value()) {
                    return set;
                }
            }
        }
        return null;
    }

    /**
     * Region-structure start replicating the game's own locate walk
     * ({@code ChunkGenerator.findNearestMapStructure} + its random-spread
     * helper, verified against 26.3 bytecode): rings expand {@code 0..bound},
     * each ring visits only its border cells in {@code dx}/{@code dz}
     * order, and the FIRST verifying candidate wins â€” the game never
     * compares distances, so a nearer start in a later-visited region
     * loses to a farther start found first. The caller applies the
     * query's max distance to that answer afterwards.
     */
    private Found findSpread(long seed, MinecraftServer server, RegistryAccess registries,
            StructureTemplateManager templates, Holder<Structure> holder, Structure structure,
            Placed placed, BlockPos center, int maxDistance, String requiredBastionType) {
        RandomSpreadStructurePlacement placement =
                (RandomSpreadStructurePlacement) placed.placement;
        int spacing = placement.spacing();
        int centerChunkX = SectionPos.blockToSectionCoord(center.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(center.getZ());
        int rings = Math.min(MAX_RINGS, maxDistance / (spacing * 16) + 2);
        int checked = 0;
        for (int ring = 0; ring <= rings; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                boolean edgeX = dx == -ring || dx == ring;
                for (int dz = -ring; dz <= ring; dz++) {
                    boolean edgeZ = dz == -ring || dz == ring;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    if (checked++ >= RECENT_CAP) {
                        return null;
                    }
                    // Chunk coords into the region lattice, exactly like the
                    // game walk; the chunk gate plus per-chunk generation
                    // below mirror what the walk verifies per candidate.
                    ChunkPos candidate = placement.getPotentialStructureChunk(seed,
                            centerChunkX + spacing * dx, centerChunkZ + spacing * dz);
                    if (!placement.isStructureChunk(placed.dim.state, candidate.x(), candidate.z())) {
                        continue;
                    }
                    if (!placement.applyAdditionalChunkRestrictions(candidate.x(), candidate.z(), seed)) {
                        continue;
                    }
                    if (!selectedAndValid(seed, registries, templates, holder, structure, placed,
                            candidate)) {
                        continue;
                    }
                    if (requiredBastionType != null && !bastionTypeMatches(seed, registries,
                            templates, holder, structure, placed, candidate, requiredBastionType)) {
                        continue;
                    }
                    BlockPos pos = placement.getLocatePos(candidate);
                    return new Found(horizontal(center, pos), 0, pos);
                }
            }
        }
        return null;
    }

    /**
     * Replicates the {@code createStructures} selection loop: weighted picks
     * in seed order; the target must be the first pick whose generation
     * succeeds, and any earlier successful pick rejects the chunk.
     */
    private boolean selectedAndValid(long seed, RegistryAccess registries,
            StructureTemplateManager templates, Holder<Structure> holder, Structure structure,
            Placed placed, ChunkPos candidate) {
        List<StructureSelectionEntry> entries = placed.set.value().structures();
        if (entries.size() == 1) {
            if (entries.get(0).structure().value() != structure) {
                return false;
            }
            return generates(seed, registries, templates, holder, structure, placed, candidate);
        }
        List<StructureSelectionEntry> remaining = new ArrayList<StructureSelectionEntry>(entries);
        int total = 0;
        for (StructureSelectionEntry entry : remaining) {
            total += entry.weight();
        }
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, candidate.x(), candidate.z());
        while (!remaining.isEmpty() && total > 0) {
            int pick = random.nextInt(total);
            int index = 0;
            for (StructureSelectionEntry entry : remaining) {
                pick -= entry.weight();
                if (pick < 0) {
                    break;
                }
                index++;
            }
            StructureSelectionEntry entry = remaining.get(index);
            Structure picked = entry.structure().value();
            boolean valid = generates(seed, registries, templates, entry.structure(), picked,
                    placed, candidate);
            if (picked == structure) {
                return valid;
            }
            if (valid) {
                return false;
            }
            remaining.remove(index);
            total -= entry.weight();
        }
        return false;
    }

    /** The game's own per-chunk generation check (references start at zero). */
    private boolean generates(long seed, RegistryAccess registries,
            StructureTemplateManager templates, Holder<Structure> holder, Structure structure,
            Placed placed, ChunkPos candidate) {
        StructureStart start =
                generateStart(seed, registries, templates, holder, structure, placed, candidate);
        return start != null && start.isValid();
    }

    /** Generates one structure start for type inspection (bastions). */
    private StructureStart generateStart(long seed, RegistryAccess registries,
            StructureTemplateManager templates, Holder<Structure> holder, Structure structure,
            Placed placed, ChunkPos candidate) {
        Predicate<Holder<Biome>> validBiome =
                biome -> structure.biomes().contains(biome);
        return structure.generate(holder, placed.dim.level.dimension(), registries,
                placed.dim.generator, placed.dim.biomeSource, placed.dim.sampler,
                placed.dim.randomState, templates, seed, candidate, 0, placed.dim.level,
                validBiome);
    }

    /** Whether the bastion at a candidate chunk has the required subtype. */
    private boolean bastionTypeMatches(long seed, RegistryAccess registries,
            StructureTemplateManager templates, Holder<Structure> holder, Structure structure,
            Placed placed, ChunkPos candidate, String requiredType) {
        try {
            StructureStart start =
                    generateStart(seed, registries, templates, holder, structure, placed, candidate);
            String type = BastionTypes263.typeOf(start);
            return type != null && requiredType.trim().equalsIgnoreCase(type);
        } catch (RuntimeException bad) {
            return false;
        }
    }

    /** Nearest valid stronghold from the exact ring positions. */
    private Found findStronghold(long seed, MinecraftServer server, RegistryAccess registries,
            StructureTemplateManager templates, Holder<Structure> holder, Structure structure,
            Placed placed, BlockPos center, int maxDistance, int requiredRing) {
        ConcentricRingsStructurePlacement rings =
                (ConcentricRingsStructurePlacement) placed.placement;
        List<ChunkPos> positions = placed.dim.state.getRingPositionsFor(rings);
        Found best = null;
        for (int index = 0; index < positions.size(); index++) {
            int ring = ringOf(index, rings.count(), rings.spread());
            if (requiredRing > 0 && ring != requiredRing) {
                continue;
            }
            ChunkPos candidate = positions.get(index);
            if (!rings.isStructureChunk(placed.dim.state, candidate.x(), candidate.z())) {
                continue;
            }
            if (!rings.applyAdditionalChunkRestrictions(candidate.x(), candidate.z(), seed)) {
                continue;
            }
            if (!generates(seed, registries, templates, holder, structure, placed, candidate)) {
                continue;
            }
            BlockPos pos = rings.getLocatePos(candidate);
            long distance = horizontal(center, pos);
            if (distance <= maxDistance && (best == null || distance < best.distance)) {
                best = new Found(distance, ring, pos);
            }
        }
        return best;
    }

    /**
     * 1-based ring of a ring-list index, replicating the placement growth
     * rule ({@code spread += 2*spread/(ring+1)}, capped at remaining count).
     */
    private static int ringOf(int index, int count, int initialSpread) {
        int spread = initialSpread;
        int ring = 0;
        int placed = 0;
        for (int at = 0; at < index; at++) {
            placed++;
            if (placed == spread) {
                ring++;
                placed = 0;
                spread += 2 * spread / (ring + 1);
                spread = Math.min(spread, count - at);
            }
        }
        return ring + 1;
    }

    private static long horizontal(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return (long) Math.sqrt(dx * dx + dz * dz);
    }

    private static String biomeId(Holder<Biome> biome) {
        if (biome == null) {
            return null;
        }
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
    }

    private static boolean isStronghold(String id) {
        return "stronghold".equals(FeatureIds263.bare(id));
    }

    private static boolean isBastion(String id) {
        return "bastion_remnant".equals(FeatureIds263.bare(id));
    }

    /** Per-dimension analysis context (live objects + per-seed state). */
    private static final class Dim {
        final ServerLevel level;
        final ChunkGenerator generator;
        final BiomeSource biomeSource;
        final RandomState randomState;
        final Climate.Sampler sampler;
        final BiomeResolver resolver;
        final ChunkGeneratorStructureState state;

        private Dim(ServerLevel level, ChunkGenerator generator, BiomeSource biomeSource,
                RandomState randomState, Climate.Sampler sampler, BiomeResolver resolver,
                ChunkGeneratorStructureState state) {
            this.level = level;
            this.generator = generator;
            this.biomeSource = biomeSource;
            this.randomState = randomState;
            this.sampler = sampler;
            this.resolver = resolver;
            this.state = state;
        }
    }

    /** A structure's home dimension, owning set and placement. */
    private static final class Placed {
        final Dim dim;
        final Holder<StructureSet> set;
        final StructurePlacement placement;

        private Placed(Dim dim, Holder<StructureSet> set, StructurePlacement placement) {
            this.dim = dim;
            this.set = set;
            this.placement = placement;
        }
    }

    private static final class Found {
        final long distance;
        final int ring;
        final BlockPos pos;

        private Found(long distance, int ring, BlockPos pos) {
            this.distance = distance;
            this.ring = ring;
            this.pos = pos;
        }
    }
}
