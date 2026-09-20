package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedFilters;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Seed analyzer for 1.21.1 (plan section 16), modelled on the verified
 * 26.3 analyzer with yarn mappings. Nearly everything is a direct call
 * into public worldgen API with per-seed noise/structure state:
 *
 * <ul>
 *   <li>Spawn: the origin chunk plus the game spawn height (no
 *       chunk-loading refinement, so predictions can sit away from the
 *       refined spawn; distances are measured from the prediction).</li>
 *   <li>Region structures: the game's own locate walk (rings expand
 *       {@code 0..bound}, border cells in {@code dx}/{@code dz} order,
 *       first verifying candidate wins — never nearest) plus per-chunk
 *       generation ({@code createStructureStart}), including multi-entry
 *       set selection replicated from the game's set loop
 *       ({@code ChunkRandom} + {@code setCarverSeed}, first success
 *       wins). Both replications were verified against 1.21.1
 *       bytecode.</li>
 *   <li>Strongholds: the exact ring positions from the placement
 *       calculator, with ring numbers from the replicated ring-growth
 *       rule.</li>
 * </ul>
 *
 * <p>Bastion subtypes come from the generated start's template, the same
 * reading live lookups use.
 *
 * <p>Stage-B verification runs for lava-constrained queries after Stage-A
 * passes: the game's own lava-spring placement pipeline executes for the
 * candidate seed over noise-stage terrain columns
 * ({@link StageBLava121}), so {@code lava.available} reflects real
 * worldgen.
 *
 * <p>Preset ids resolve tag-first: family ids like {@code village} name a
 * structure tag on modern versions, so each member is searched and the
 * nearest wins. Ids without a tag resolve directly.
 *
 * <p>{@code location.<id>} findings carry {@code getLocatePos} X/Z (exactly
 * what {@code /locate} reports); Y is conventional (overworld sea level,
 * 64 in the Nether/End), not a prediction.
 *
 * <p>Threading: every call builds fresh noise/structure state, so
 * concurrent search workers share nothing mutable. Live registries,
 * generators, sources and template managers are only read. Calls need a
 * loaded server (any single-player world) for those live objects.
 */
public final class SeedAnalyzer121 implements SeedAnalyzer {
    /** Region-ring cap around the search center (same bound as 1.16.1). */
    private static final int MAX_RINGS = 64;
    /** Candidate-chunk cap per structure per seed. */
    private static final int RECENT_CAP = 4096;
    /** End search center: the main island, like 1.16.1. */
    private static final BlockPos END_CENTER = new BlockPos(100, 49, 0);

    private final LiveAdapter121 live;

    public SeedAnalyzer121(LiveAdapter121 live) {
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
        ServerWorld overworld = server.getOverworld();
        LiveWorld121 current = live.currentWorld();
        if (overworld == null) {
            return SeedAnalysis.mismatch();
        }
        DynamicRegistryManager registries = server.getRegistryManager();
        RegistryWrapper<DoublePerlinNoiseSampler.NoiseParameters> noises;
        RegistryWrapper<StructureSet> sets;
        Registry<Structure> structures;
        try {
            noises = registries.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS);
            sets = registries.getWrapperOrThrow(RegistryKeys.STRUCTURE_SET);
            structures = registries.get(RegistryKeys.STRUCTURE);
        } catch (RuntimeException missing) {
            return SeedAnalysis.mismatch();
        }
        if (structures == null) {
            return SeedAnalysis.mismatch();
        }
        StructureTemplateManager templates;
        try {
            templates = server.getStructureTemplateManager();
        } catch (RuntimeException missing) {
            return SeedAnalysis.mismatch();
        }
        Dim over = dim(overworld, seed, noises, sets);
        if (over == null) {
            return SeedAnalysis.mismatch();
        }
        ServerWorld netherLevel = server.getWorld(World.NETHER);
        ServerWorld endLevel = server.getWorld(World.END);
        Dim nether = netherLevel == null ? null : dim(netherLevel, seed, noises, sets);
        Dim end = endLevel == null ? null : dim(endLevel, seed, noises, sets);

        Map<String, Object> findings = new LinkedHashMap<String, Object>();
        BlockPos spawn = predictSpawn(over, seed, current);
        String spawnBiome = biomeId(over.biomeSource.getBiome(
                spawn.getX() >> 2, spawn.getY() >> 2, spawn.getZ() >> 2, over.sampler));
        if (spawnBiome == null) {
            return SeedAnalysis.mismatch();
        }
        findings.put(SeedFilters.FIND_BIOME_SPAWN, spawnBiome);
        boolean ok = query.requiredBiome() == null
                || FeatureIds121.bare(query.requiredBiome()).equals(FeatureIds121.bare(spawnBiome));
        boolean sawBastion = false;
        boolean sawStronghold = false;

        for (Map.Entry<String, Integer> required : query.requiredStructures().entrySet()) {
            String id = required.getKey();
            int maxDistance = required.getValue() == null ? Integer.MAX_VALUE : required.getValue();
            List<RegistryEntry<Structure>> candidates;
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
            Structure bestStructure = null;
            boolean placementSkipped = false;
            for (RegistryEntry<Structure> holder : candidates) {
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
                    found = findStronghold(seed, registries, templates, holder, structure,
                            placed, center, maxDistance, query.strongholdRing());
                } else if (placed.placement instanceof RandomSpreadStructurePlacement) {
                    sawBastion |= isBastion(member);
                    String requiredType =
                            isBastion(member) ? query.bastionType() : null;
                    found = findSpread(seed, registries, templates, holder, structure,
                            placed, center, maxDistance, requiredType);
                } else {
                    placementSkipped = true;
                    continue;
                }
                if (found != null && found.distance <= maxDistance
                        && (best == null || found.distance < best.distance)) {
                    best = found;
                    bestPlaced = placed;
                    bestStructure = structure;
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
            if (isBastion(id)) {
                try {
                    String type = BastionTypes121.typeOf(generateStart(registries, templates,
                            bestStructure, bestPlaced, best.candidate));
                    if (type != null) {
                        findings.put(SeedFilters.FIND_BASTION_TYPE, type);
                    }
                } catch (RuntimeException ignored) {
                    // The required subtype was already checked during search;
                    // a missing reporting value must not change seed matching.
                }
            }
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
                lava = StageBLava121.verify(server, overworld, over.generator, over.noiseConfig,
                        over.biomeSource, over.sampler, spawn, seed);
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

    /** Per-dimension reusable live objects plus per-seed noise state. */
    private static Dim dim(ServerWorld level, long seed,
            RegistryWrapper<DoublePerlinNoiseSampler.NoiseParameters> noises,
            RegistryWrapper<StructureSet> sets) {
        ChunkGenerator generator = level.getChunkManager().getChunkGenerator();
        if (!(generator instanceof NoiseChunkGenerator)) {
            return null;
        }
        ChunkGeneratorSettings settings =
                ((NoiseChunkGenerator) generator).getSettings().value();
        NoiseConfig noiseConfig;
        StructurePlacementCalculator calculator;
        try {
            noiseConfig = NoiseConfig.create(settings, noises, seed);
            calculator = generator.createStructurePlacementCalculator(sets, noiseConfig, seed);
            calculator.tryCalculate();
        } catch (RuntimeException bad) {
            return null;
        }
        return new Dim(level, generator, generator.getBiomeSource(), noiseConfig,
                noiseConfig.getMultiNoiseSampler(), calculator);
    }

    /**
     * Use the server-refined spawn for an active candidate world.  A normal
     * seed search has no generated candidate world, so it retains the cheap
     * deterministic origin fallback used by the analyzer contract.  The
     * structure search continues to use the stable loaded lobby adapters.
     */
    private static BlockPos predictSpawn(Dim over, long seed, LiveWorld121 current) {
        if (current != null && current.world().getSeed() == seed) {
            return current.world().getSpawnPos();
        }
        int y = over.generator.getSpawnHeight(over.level);
        return new BlockPos(8, y, 8);
    }

    private static double locationY(Placed placed, ServerWorld overworld) {
        if (placed.dim.level.getRegistryKey().equals(World.OVERWORLD)) {
            return (double) overworld.getSeaLevel();
        }
        return 64.0;
    }

    /**
     * Resolves a preset id to the structures it names: the structure tag
     * first (family ids keep their whole-family meaning this way),
     * otherwise the single structure. Unknown ids resolve to no
     * candidates. Findings still use the preset id verbatim so shared
     * filters keep matching.
     */
    private static List<RegistryEntry<Structure>> resolveCandidates(Registry<Structure> structures,
            String id) throws PracticeException {
        try {
            Optional<RegistryEntryList.Named<Structure>> tag =
                    structures.getEntryList(FeatureIds121.tagKey(id));
            if (tag != null && tag.isPresent() && tag.get().size() > 0) {
                List<RegistryEntry<Structure>> members =
                        new ArrayList<RegistryEntry<Structure>>(tag.get().size());
                for (RegistryEntry<Structure> member : tag.get()) {
                    members.add(member);
                }
                return members;
            }
        } catch (RuntimeException bad) {
            return new ArrayList<RegistryEntry<Structure>>(0);
        }
        try {
            Optional<RegistryEntry.Reference<Structure>> direct =
                    structures.getEntry(FeatureIds121.key(id));
            if (direct != null && direct.isPresent()) {
                List<RegistryEntry<Structure>> single = new ArrayList<RegistryEntry<Structure>>(1);
                single.add(direct.get());
                return single;
            }
        } catch (RuntimeException bad) {
            return new ArrayList<RegistryEntry<Structure>>(0);
        }
        return new ArrayList<RegistryEntry<Structure>>(0);
    }

    /** Registry id of one resolved member (falls back to the preset id). */
    private static String memberId(RegistryEntry<Structure> holder, String presetId) {
        if (holder == null || holder.getKey() == null || !holder.getKey().isPresent()) {
            return presetId;
        }
        return holder.getKey().get().getValue().toString();
    }

    /** Finds the dimension whose sets place this structure (vanilla: unique). */
    private static Placed locateHome(RegistryEntry<Structure> holder, Dim over, Dim nether, Dim end) {
        Dim[] dims = new Dim[]{over, nether, end};
        for (Dim dim : dims) {
            if (dim == null) {
                continue;
            }
            List<StructurePlacement> placements = dim.calculator.getPlacements(holder);
            if (placements.isEmpty()) {
                continue;
            }
            StructurePlacement placement = placements.get(0);
            RegistryEntry<StructureSet> set = owningSet(dim, holder, placement);
            if (set != null) {
                return new Placed(dim, set, placement);
            }
        }
        return null;
    }

    private static RegistryEntry<StructureSet> owningSet(Dim dim, RegistryEntry<Structure> holder,
            StructurePlacement placement) {
        for (RegistryEntry<StructureSet> set : dim.calculator.getStructureSets()) {
            if (set.value().placement() != placement) {
                continue;
            }
            for (StructureSet.WeightedEntry entry : set.value().structures()) {
                if (entry.structure().value() == holder.value()) {
                    return set;
                }
            }
        }
        return null;
    }

    /**
     * Region-structure start replicating the game's own locate walk
     * ({@code ChunkGenerator.locateStructure} + its random-spread helper,
     * verified against 1.21.1 bytecode): rings expand {@code 0..bound},
     * each ring visits only its border cells in {@code dx}/{@code dz}
     * order, and the FIRST verifying candidate wins — the game never
     * compares distances, so a nearer start in a later-visited region
     * loses to a farther start found first. The caller applies the
     * query's max distance to that answer afterwards.
     */
    private Found findSpread(long seed, DynamicRegistryManager registries,
            StructureTemplateManager templates, RegistryEntry<Structure> holder,
            Structure structure, Placed placed, BlockPos center, int maxDistance,
            String requiredBastionType) {
        RandomSpreadStructurePlacement placement =
                (RandomSpreadStructurePlacement) placed.placement;
        int spacing = placement.getSpacing();
        int centerChunkX = ChunkSectionPos.getSectionCoord(center.getX());
        int centerChunkZ = ChunkSectionPos.getSectionCoord(center.getZ());
        long structureSeed = placed.dim.calculator.getStructureSeed();
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
                    // game walk (no shouldGenerate call there either: a
                    // getStartChunk output is a start chunk by construction).
                    ChunkPos candidate = placement.getStartChunk(structureSeed,
                            centerChunkX + spacing * dx, centerChunkZ + spacing * dz);
                    if (!selectedAndValid(seed, registries, templates, holder, structure, placed,
                            candidate)) {
                        continue;
                    }
                    if (requiredBastionType != null && !bastionTypeMatches(seed, registries,
                            templates, holder, structure, placed, candidate, requiredBastionType)) {
                        continue;
                    }
                    BlockPos pos = placement.getLocatePos(candidate);
                    return new Found(horizontal(center, pos), 0, pos, candidate);
                }
            }
        }
        return null;
    }

    /**
     * Replicates the game's set loop: weighted picks in seed order; the
     * target must be the first pick whose generation succeeds, and any
     * earlier successful pick rejects the chunk.
     */
    private boolean selectedAndValid(long seed, DynamicRegistryManager registries,
            StructureTemplateManager templates, RegistryEntry<Structure> holder,
            Structure structure, Placed placed, ChunkPos candidate) {
        List<StructureSet.WeightedEntry> entries = placed.set.value().structures();
        if (entries.size() == 1) {
            if (entries.get(0).structure().value() != structure) {
                return false;
            }
            return generates(registries, templates, structure, placed, candidate);
        }
        List<StructureSet.WeightedEntry> remaining =
                new ArrayList<StructureSet.WeightedEntry>(entries);
        int total = 0;
        for (StructureSet.WeightedEntry entry : remaining) {
            total += entry.weight();
        }
        ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
        random.setCarverSeed(placed.dim.calculator.getStructureSeed(),
                candidate.x, candidate.z);
        while (!remaining.isEmpty() && total > 0) {
            int pick = random.nextInt(total);
            int index = 0;
            for (StructureSet.WeightedEntry entry : remaining) {
                pick -= entry.weight();
                if (pick < 0) {
                    break;
                }
                index++;
            }
            StructureSet.WeightedEntry entry = remaining.get(index);
            Structure picked = entry.structure().value();
            boolean valid = generates(registries, templates, picked, placed, candidate);
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
    private boolean generates(DynamicRegistryManager registries,
            StructureTemplateManager templates, Structure structure, Placed placed,
            ChunkPos candidate) {
        StructureStart start =
                generateStart(registries, templates, structure, placed, candidate);
        return start != null && start.hasChildren();
    }

    /** Generates one structure start for type inspection (bastions). */
    private StructureStart generateStart(DynamicRegistryManager registries,
            StructureTemplateManager templates, Structure structure, Placed placed,
            ChunkPos candidate) {
        Predicate<RegistryEntry<Biome>> validBiome =
                new Predicate<RegistryEntry<Biome>>() {
                    @Override
                    public boolean test(RegistryEntry<Biome> biome) {
                        return structure.getValidBiomes().contains(biome);
                    }
                };
        return structure.createStructureStart(registries, placed.dim.generator,
                placed.dim.biomeSource, placed.dim.noiseConfig, templates,
                placed.dim.calculator.getStructureSeed(), candidate, 0, placed.dim.level,
                validBiome);
    }

    /** Whether the bastion at a candidate chunk has the required subtype. */
    private boolean bastionTypeMatches(long seed, DynamicRegistryManager registries,
            StructureTemplateManager templates, RegistryEntry<Structure> holder,
            Structure structure, Placed placed, ChunkPos candidate, String requiredType) {
        try {
            StructureStart start =
                    generateStart(registries, templates, structure, placed, candidate);
            String type = BastionTypes121.typeOf(start);
            return type != null && requiredType.trim().equalsIgnoreCase(type);
        } catch (RuntimeException bad) {
            return false;
        }
    }

    /** Nearest valid stronghold from the exact ring positions. */
    private Found findStronghold(long seed, DynamicRegistryManager registries,
            StructureTemplateManager templates, RegistryEntry<Structure> holder,
            Structure structure, Placed placed, BlockPos center, int maxDistance,
            int requiredRing) {
        ConcentricRingsStructurePlacement rings =
                (ConcentricRingsStructurePlacement) placed.placement;
        List<ChunkPos> positions = placed.dim.calculator.getPlacementPositions(rings);
        Found best = null;
        for (int index = 0; index < positions.size(); index++) {
            int ring = ringOf(index, rings.getCount(), rings.getSpread());
            if (requiredRing > 0 && ring != requiredRing) {
                continue;
            }
            ChunkPos candidate = positions.get(index);
            if (!rings.shouldGenerate(placed.dim.calculator, candidate.x, candidate.z)) {
                continue;
            }
            if (!generates(registries, templates, structure, placed, candidate)) {
                continue;
            }
            BlockPos pos = rings.getLocatePos(candidate);
            long distance = horizontal(center, pos);
            if (distance <= maxDistance && (best == null || distance < best.distance)) {
                best = new Found(distance, ring, pos, candidate);
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

    private static String biomeId(RegistryEntry<Biome> biome) {
        if (biome == null || biome.getKey() == null || !biome.getKey().isPresent()) {
            return null;
        }
        return biome.getKey().get().getValue().toString();
    }

    private static boolean isStronghold(String id) {
        return "stronghold".equals(FeatureIds121.bare(id));
    }

    private static boolean isBastion(String id) {
        return "bastion_remnant".equals(FeatureIds121.bare(id));
    }

    /** Per-dimension analysis context (live objects + per-seed state). */
    private static final class Dim {
        final ServerWorld level;
        final ChunkGenerator generator;
        final BiomeSource biomeSource;
        final NoiseConfig noiseConfig;
        final MultiNoiseUtil.MultiNoiseSampler sampler;
        final StructurePlacementCalculator calculator;

        private Dim(ServerWorld level, ChunkGenerator generator, BiomeSource biomeSource,
                NoiseConfig noiseConfig, MultiNoiseUtil.MultiNoiseSampler sampler,
                StructurePlacementCalculator calculator) {
            this.level = level;
            this.generator = generator;
            this.biomeSource = biomeSource;
            this.noiseConfig = noiseConfig;
            this.sampler = sampler;
            this.calculator = calculator;
        }
    }

    /** A structure's home dimension, owning set and placement. */
    private static final class Placed {
        final Dim dim;
        final RegistryEntry<StructureSet> set;
        final StructurePlacement placement;

        private Placed(Dim dim, RegistryEntry<StructureSet> set, StructurePlacement placement) {
            this.dim = dim;
            this.set = set;
            this.placement = placement;
        }
    }

    private static final class Found {
        final long distance;
        final int ring;
        final BlockPos pos;
        final ChunkPos candidate;

        private Found(long distance, int ring, BlockPos pos, ChunkPos candidate) {
            this.distance = distance;
            this.ring = ring;
            this.pos = pos;
            this.candidate = candidate;
        }
    }
}
