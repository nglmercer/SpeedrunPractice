package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedFilters;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.ptlib.PTLib;
import com.gregor0410.speedrunpractice.mixin.DimensionTypeAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.TheEndBiomeSource;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.chunk.StructureConfig;
import net.minecraft.world.gen.chunk.StrongholdConfig;
import net.minecraft.world.gen.chunk.StructuresConfig;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Real 1.16.1 seed math (plan step 09). Every rule below was transcribed
 * from the game's own generation code and mirrors what practice worlds do:
 *
 * <ul>
 *   <li>Spawn: the same {@code locateBiome} start-chunk call vanilla setup
 *       makes (terrain validation needs real chunks, so the predicted spawn
 *       is the located pos itself — same biome except at biome borders).</li>
 *   <li>Region structures: the exact region walk ({@code method_27218}),
 *       the uniform {@code hasStructureFeature} biome gate, and the exact
 *       extra rolls (bastion/fortress split incl. PTLib rarity, mineshaft
 *       chance, buried-treasure chance incl. the PTLib override).</li>
 *   <li>Strongholds: the exact 128-ring placement incl. biome nudging.</li>
 *   <li>Bastion subtype: the exact piece-generation draw (first
 *       {@code nextInt} on the start's carver-seeded random over the
 *       PTLib-enabled type list), verified against the game's bytecode.</li>
 * </ul>
 *
 * <p>Distances are horizontal blocks from the predicted spawn (nether
 * structures from spawn/8). Pillager-outpost village avoidance and
 * end-city terrain checks are not replicated (treated as valid when the
 * biome gate passes). Lava findings are omitted because lava needs
 * generated chunks: lava-constrained queries always mismatch here and must
 * run through a chunk-generating stage-B verifier instead.
 *
 * <p>{@code location.<id>} findings carry the predicted start-chunk center.
 * X/Z come from the placement math; Y is conventional (overworld sea level,
 * 64 in the Nether/End), not a prediction, because surface height needs
 * generated chunks.
 *
 * <p>Threading: every call builds fresh sources/randoms, so concurrent
 * search workers share nothing mutable. Calls need a loaded server (any
 * single-player world) for configs and the nether source.
 */
public final class SeedAnalyzer116 implements SeedAnalyzer {
    /** Region rings searched past the query distance before giving up. */
    private static final int MAX_RINGS = 1000;

    private static final Set<String> BADLANDS = badlands();

    private static Set<String> badlands() {
        Set<String> biomes = new HashSet<String>();
        biomes.add("badlands");
        biomes.add("badlands_plateau");
        biomes.add("modified_badlands_plateau");
        biomes.add("wooded_badlands_plateau");
        biomes.add("eroded_badlands");
        return biomes;
    }

    private final LiveAdapter116 adapter;

    public SeedAnalyzer116(LiveAdapter116 adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        this.adapter = adapter;
    }

    @Override
    public boolean matches(long seed, SeedQuery query) {
        return analyze(seed, query).matches();
    }

    @Override
    public SeedAnalysis analyze(long seed, SeedQuery query) {
        if (query == null) {
            return SeedAnalysis.mismatch();
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            return SeedAnalysis.mismatch();
        }
        ServerWorld overworld = server.getOverworld();
        ServerWorld nether = server.getWorld(World.NETHER);
        ServerWorld end = server.getWorld(World.END);
        if (overworld == null || nether == null) {
            return SeedAnalysis.mismatch();
        }
        try {
            return analyzeLive(seed, query, overworld, nether, end);
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Seed analysis failed for " + seed + ": " + bad.getMessage());
            return SeedAnalysis.mismatch();
        }
    }

    private SeedAnalysis analyzeLive(long seed, SeedQuery query, ServerWorld overworld,
                                     ServerWorld nether, ServerWorld end) {
        // Practice worlds build these exact sources per seed (see the server
        // mixin); the live configs capture PTLib/datapack spacing tweaks.
        BiomeSource overSource = new VanillaLayeredBiomeSource(seed, false, false);
        // NOTE: BiomeSource.withSeed is client-only in 1.16.1 (stripped on
        // dedicated servers); build the practice-seed nether source the same
        // way practice worlds do instead.
        BiomeSource netherSource = DimensionTypeAccess.invokeCreateNetherGenerator(seed)
                .getBiomeSource();
        StructuresConfig overConfig = overworld.getChunkManager().getChunkGenerator().getConfig();
        StructuresConfig netherConfig = nether.getChunkManager().getChunkGenerator().getConfig();

        if (query.lavaRequired()) {
            return SeedAnalysis.mismatch();
        }
        Map<String, Object> findings = new LinkedHashMap<String, Object>();
        BlockPos spawn = predictSpawn(overSource, overworld.getSeaLevel(), seed);
        String spawnBiome = biomeId(biomeAt(overSource, spawn.getX(), spawn.getZ()));
        findings.put(SeedFilters.FIND_BIOME_SPAWN, spawnBiome);
        boolean ok = query.requiredBiome() == null
                || FeatureIds.bare(query.requiredBiome()).equals(FeatureIds.bare(spawnBiome));
        boolean sawBastion = false;
        boolean sawStronghold = false;

        for (Map.Entry<String, Integer> required : query.requiredStructures().entrySet()) {
            String id = required.getKey();
            int maxDistance = required.getValue() == null ? Integer.MAX_VALUE : required.getValue();
            StructureFeature<?> feature;
            try {
                feature = FeatureIds.resolve(id);
            } catch (PracticeException unknown) {
                return SeedAnalysis.mismatch();
            }
            PracticeDimension home = FeatureIds.homeDimension(id);
            Found found;
            if (feature == StructureFeature.STRONGHOLD) {
                sawStronghold = true;
                found = findStronghold(seed, overSource, overConfig, spawn, maxDistance,
                        query.strongholdRing());
            } else if (home == PracticeDimension.NETHER) {
                BlockPos netherSpawn = new BlockPos(spawn.getX() / 8, 64, spawn.getZ() / 8);
                found = findRegional(seed, feature, netherSource, netherConfig, netherSpawn,
                        maxDistance, query.bastionType());
            } else if (home == PracticeDimension.END) {
                found = findEndStructure(seed, feature, end, maxDistance);
            } else {
                found = findRegional(seed, feature, overSource, overConfig, spawn, maxDistance, null);
            }
            if (found == null) {
                return SeedAnalysis.mismatch();
            }
            if (found.distance > maxDistance) {
                return SeedAnalysis.mismatch();
            }
            findings.put(SeedFilters.FIND_STRUCTURE_PREFIX + id + SeedFilters.FIND_STRUCTURE_SUFFIX,
                    found.distance);
            findings.put("location." + id, new PracticePosition(
                    found.start.x * 16 + 8, locationY(home, overworld.getSeaLevel()),
                    found.start.z * 16 + 8));
            if (feature == StructureFeature.STRONGHOLD && found.ring > 0) {
                findings.put(SeedFilters.FIND_STRONGHOLD_RING, (long) found.ring);
            }
            if (feature == StructureFeature.BASTION_REMNANT) {
                sawBastion = true;
                String type = predictBastionType(seed, found.start);
                if (type != null) {
                    findings.put(SeedFilters.FIND_BASTION_TYPE, type);
                }
            }
        }
        if (query.bastionType() != null && !sawBastion) {
            return SeedAnalysis.mismatch();
        }
        if (query.strongholdRing() > 0 && !sawStronghold) {
            return SeedAnalysis.mismatch();
        }
        return SeedAnalysis.of(ok, findings);
    }

    /** Conventional Y for predicted locations (see the class javadoc). */
    private static double locationY(PracticeDimension home, int seaLevel) {
        return home == PracticeDimension.OVERWORLD ? (double) seaLevel : 64.0;
    }

    /** Vanilla's spawn start-chunk call; the located pos predicts the spawn. */
    private static BlockPos predictSpawn(BiomeSource source, int seaLevel, long seed) {
        BlockPos found;
        try {
            found = source.locateBiome(0, seaLevel, 0, 256, source.getSpawnBiomes(), new Random(seed));
        } catch (RuntimeException bad) {
            return new BlockPos(0, seaLevel, 0);
        }
        return found == null ? new BlockPos(0, seaLevel, 0) : found;
    }

    private static Biome biomeAt(BiomeSource source, int x, int z) {
        return source.getBiomeForNoiseGen(x >> 2, 0, z >> 2);
    }

    private static String biomeId(Biome biome) {
        return Registry.BIOME.getId(biome).toString();
    }

    /**
     * Nearest region-structure start, replicating the vanilla ring walk.
     * {@code requiredBastionType} only constrains bastion starts (ignored
     * for every other feature).
     */
    private Found findRegional(long seed, StructureFeature<?> feature, BiomeSource source,
                               StructuresConfig config, BlockPos center, int maxDistance,
                               String requiredBastionType) {
        if (!source.hasStructureFeature(feature)) {
            return null;
        }
        StructureConfig entry = config.method_28600(feature);
        if (entry == null) {
            return null;
        }
        int spacing = Math.max(1, entry.getSpacing());
        int centerChunkX = Math.floorDiv(center.getX(), 16);
        int centerChunkZ = Math.floorDiv(center.getZ(), 16);
        int rings = maxDistance >= Integer.MAX_VALUE / 2
                ? MAX_RINGS
                : Math.min(MAX_RINGS, maxDistance / (16 * spacing) + 2);
        ChunkRandom random = new ChunkRandom();
        Found best = null;
        for (int ring = 0; ring <= rings; ring++) {
            for (int ox = -ring; ox <= ring; ox++) {
                for (int oz = -ring; oz <= ring; oz++) {
                    if (ring != 0 && ox != -ring && ox != ring && oz != -ring && oz != ring) {
                        continue;
                    }
                    ChunkPos start = feature.method_27218(entry, seed, random,
                            centerChunkX + spacing * ox, centerChunkZ + spacing * oz);
                    Biome biome = source.getBiomeForNoiseGen((start.x << 2) + 2, 0, (start.z << 2) + 2);
                    if (!biome.hasStructureFeature(feature)) {
                        continue;
                    }
                    if (!extraValid(feature, seed, start, random, biome)) {
                        continue;
                    }
                    if (feature == StructureFeature.BASTION_REMNANT && requiredBastionType != null
                            && !requiredBastionType.equalsIgnoreCase(predictBastionType(seed, start))) {
                        continue;
                    }
                    long distance = horizontal(center, start);
                    if (best == null || distance < best.distance) {
                        best = new Found(distance, 0, start);
                    }
                }
            }
            if (best != null && ringMinDistance(ring + 1, spacing) > maxDistance
                    && best.distance <= maxDistance) {
                return best;
            }
        }
        return best;
    }

    private static long ringMinDistance(int ring, int spacing) {
        if (ring <= 0) {
            return 0L;
        }
        return (long) (ring - 1) * spacing * 16L;
    }

    private static long horizontal(BlockPos center, ChunkPos start) {
        long dx = (long) (start.x * 16 + 8) - center.getX();
        long dz = (long) (start.z * 16 + 8) - center.getZ();
        return (long) Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Per-feature extra rolls after the biome gate, transcribed from each
     * feature's {@code shouldStartAt} (incl. the PTLib overrides that ship
     * with this mod). The region random continues exactly where vanilla's
     * generation leaves it.
     */
    private boolean extraValid(StructureFeature<?> feature, long seed, ChunkPos start,
                               ChunkRandom random, Biome biome) {
        if (feature == StructureFeature.BASTION_REMNANT) {
            com.gregor0410.ptlib.config.PTConfig config = PTLib.getConfig();
            if (!(config.isBridge() || config.isHousing() || config.isTreasure() || config.isStables())) {
                return false;
            }
            if (config.getBastionRarity() == 60) {
                return random.nextInt(5) >= 2;
            }
            return random.nextInt(100) >= (100 - config.getBastionRarity());
        }
        if (feature == StructureFeature.FORTRESS) {
            int rarity = PTLib.getConfig().getBastionRarity();
            if (rarity == 60) {
                return random.nextInt(5) < 2;
            }
            return random.nextInt(100) < rarity;
        }
        if (feature == StructureFeature.MINESHAFT) {
            ChunkRandom roll = new ChunkRandom();
            roll.setCarverSeed(seed, start.x, start.z);
            double probability = BADLANDS.contains(FeatureIds.bare(biomeId(biome))) ? 0.01 : 0.004;
            return roll.nextDouble() < probability;
        }
        if (feature == StructureFeature.BURIED_TREASURE) {
            // Vanilla draws once, then the PTLib override draws again and
            // compares against the configured probability (default 0.01).
            ChunkRandom roll = new ChunkRandom();
            roll.setRegionSeed(seed, start.x, start.z, 10387320);
            roll.nextFloat();
            return roll.nextFloat() < PTLib.getConfig().getBuriedTreasureProbability();
        }
        return true;
    }

    /**
     * Exact 128-ring stronghold placement incl. biome nudging.
     * {@code requiredRing} is 1-based (0 = any ring).
     */
    private Found findStronghold(long seed, BiomeSource source, StructuresConfig config,
                                 BlockPos center, int maxDistance, int requiredRing) {
        if (!source.hasStructureFeature(StructureFeature.STRONGHOLD)) {
            return null;
        }
        StrongholdConfig stronghold = config.getStronghold();
        if (stronghold == null || stronghold.getCount() == 0) {
            return null;
        }
        List<Biome> valid = new ArrayList<Biome>();
        for (Biome biome : source.method_28443()) {
            if (biome.hasStructureFeature(StructureFeature.STRONGHOLD)) {
                valid.add(biome);
            }
        }
        int distance = stronghold.getDistance();
        int count = stronghold.getCount();
        int ringSize = stronghold.getSpread();
        Random random = new Random();
        random.setSeed(seed);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int placed = 0;
        int ring = 0;
        Found best = null;
        for (int i = 0; i < count; i++) {
            double radius = 4 * distance + distance * ring * 6
                    + (random.nextDouble() - 0.5D) * distance * 2.5D;
            int cx = (int) Math.round(Math.cos(angle) * radius);
            int cz = (int) Math.round(Math.sin(angle) * radius);
            BlockPos nudged = source.locateBiome(cx << 4 + 8, 0, cz << 4 + 8, 112, valid, random);
            if (nudged != null) {
                cx = nudged.getX() >> 4;
                cz = nudged.getZ() >> 4;
            }
            if (requiredRing > 0 && ring + 1 != requiredRing) {
                // Still advance the placement state below; only the match skips.
            } else {
                ChunkPos at = new ChunkPos(cx, cz);
                long d = horizontal(center, at);
                if (d <= maxDistance && (best == null || d < best.distance)) {
                    best = new Found(d, ring + 1, at);
                }
            }
            angle += 2.0D * Math.PI / ringSize;
            placed++;
            if (placed == ringSize) {
                ring++;
                placed = 0;
                ringSize += 2 * ringSize / (ring + 1);
                ringSize = Math.min(ringSize, count - i);
                angle += random.nextDouble() * Math.PI * 2.0D;
            }
        }
        return best;
    }

    private Found findEndStructure(long seed, StructureFeature<?> feature, ServerWorld end, int maxDistance) {
        if (end == null) {
            return null;
        }
        BiomeSource source = new TheEndBiomeSource(seed);
        StructuresConfig config = end.getChunkManager().getChunkGenerator().getConfig();
        return findRegional(seed, feature, source, config, new BlockPos(100, 49, 0), maxDistance, null);
    }

    /**
     * Bastion subtype for one start chunk, replicating piece generation:
     * {@link net.minecraft.structure.StructureStart} seeds its random with
     * {@code setCarverSeed(seed, chunkX, chunkZ)}, and
     * {@link net.minecraft.structure.BastionRemnantGenerator#addPieces} spends
     * its first draw on {@code nextInt(possibleConfigs.size())} — with PTLib's
     * mixin replacing the list by the enabled types in housing, stables,
     * treasure, bridge order. Null when every type is disabled (no bastion
     * can generate then).
     */
    private static String predictBastionType(long seed, ChunkPos start) {
        com.gregor0410.ptlib.config.PTConfig config = PTLib.getConfig();
        List<String> enabled = new ArrayList<String>();
        if (config.isHousing()) {
            enabled.add("housing");
        }
        if (config.isStables()) {
            enabled.add("stables");
        }
        if (config.isTreasure()) {
            enabled.add("treasure");
        }
        if (config.isBridge()) {
            enabled.add("bridge");
        }
        if (enabled.isEmpty()) {
            return null;
        }
        ChunkRandom random = new ChunkRandom();
        random.setCarverSeed(seed, start.x, start.z);
        return enabled.get(random.nextInt(enabled.size()));
    }

    private static final class Found {
        final long distance;
        final int ring;
        final ChunkPos start;

        private Found(long distance, int ring, ChunkPos start) {
            this.distance = distance;
            this.ring = ring;
            this.start = start;
        }
    }
}
