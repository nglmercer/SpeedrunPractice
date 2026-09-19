package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter.StructureLocation;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.mojang.datafixers.util.Pair;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StrongholdGenerator;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.level.LevelProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Structure location on the 1.21.1 chunk generator. Every returned position
 * is a genuine {@code locateStructure} hit — multi-results come from
 * re-querying ringed centers and de-duplicating, never from invented data.
 * Family ids ({@code village}, {@code mineshaft}...) resolve through the
 * structure tag first, matching seed search; each member is queried and the
 * nearest wins.
 *
 * <p>Metadata: strongholds carry the portal-room center
 * ({@code portal_room} as {@code "x,y,z"}) from the generated start, and
 * bastions carry their subtype ({@code bastion_type} as {@code treasure},
 * {@code bridge}, {@code housing} or {@code stables}) from the start
 * piece's template. Both degrade gracefully when the pieces are unreadable.
 */
final class LiveStructures121 implements StructureAdapter {
    private final LiveAdapter121 adapter;

    LiveStructures121(LiveAdapter121 adapter) {
        this.adapter = adapter;
    }

    @Override
    public Optional<StructureLocation> locateNearest(PracticeWorld world, StructureQuery query)
            throws PracticeException {
        if (!(world instanceof LiveWorld121)) {
            throw new PracticeException("locateNearest got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            throw new PracticeException("locateNearest with no running server",
                    "Cannot locate structures: no world is loaded. Open a single-player world first.");
        }
        List<RegistryEntry<Structure>> candidates = resolveCandidates(server, query.structureId());
        if (candidates.isEmpty()) {
            throw new PracticeException("Unknown structure \"" + query.structureId() + "\"",
                    "Unknown structure \"" + query.structureId() + "\". Try village, shipwreck,"
                            + " buried_treasure, ruined_portal, bastion_remnant, fortress or stronghold.");
        }
        ServerWorld backing = generatorWorld((LiveWorld121) world, query.structureId());
        BlockPos center = centerOf(backing, query);
        Found best = null;
        for (RegistryEntry<Structure> holder : candidates) {
            Pair<BlockPos, RegistryEntry<Structure>> hit = backing.getChunkManager()
                    .getChunkGenerator().locateStructure(backing,
                            RegistryEntryList.of(holder), center,
                            Math.max(query.radius(), 1), false);
            if (hit == null || hit.getFirst() == null) {
                continue;
            }
            long distance = horizontal(center, hit.getFirst());
            if (best == null || distance < best.distance) {
                best = new Found(hit.getFirst(), distance, holder.value());
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(location(backing, query.structureId(), best));
    }

    @Override
    public List<StructureLocation> locate(PracticeWorld world, StructureQuery query, int limit)
            throws PracticeException {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
        if (!(world instanceof LiveWorld121)) {
            throw new PracticeException("locate got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            throw new PracticeException("locate with no running server",
                    "Cannot locate structures: no world is loaded. Open a single-player world first.");
        }
        List<RegistryEntry<Structure>> candidates = resolveCandidates(server, query.structureId());
        if (candidates.isEmpty()) {
            throw new PracticeException("Unknown structure \"" + query.structureId() + "\"",
                    "Unknown structure \"" + query.structureId() + "\". Try village, shipwreck,"
                            + " buried_treasure, ruined_portal, bastion_remnant, fortress or stronghold.");
        }
        List<StructureLocation> out = new ArrayList<StructureLocation>();
        List<BlockPos> seen = new ArrayList<BlockPos>();
        ServerWorld backing = generatorWorld((LiveWorld121) world, query.structureId());
        BlockPos center = centerOf(backing, query);
        int radius = Math.max(query.radius(), 1);
        int[] rings = new int[] {0, radius / 4, radius / 2, (radius * 3) / 4};
        int[][] dirs = new int[][] {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        outer:
        for (int ring : rings) {
            if (ring == 0) {
                if (collect(backing, candidates, center, query, out, seen, limit)) {
                    break;
                }
                continue;
            }
            for (int[] dir : dirs) {
                BlockPos probe = center.add(dir[0] * ring, 0, dir[1] * ring);
                if (collect(backing, candidates, probe, query, out, seen, limit)) {
                    break outer;
                }
            }
        }
        return out;
    }

    private boolean collect(ServerWorld backing, List<RegistryEntry<Structure>> candidates,
            BlockPos probe, StructureQuery query, List<StructureLocation> out,
            List<BlockPos> seen, int limit) {
        if (out.size() >= limit) {
            return true;
        }
        for (RegistryEntry<Structure> holder : candidates) {
            if (out.size() >= limit) {
                return true;
            }
            Pair<BlockPos, RegistryEntry<Structure>> hit = backing.getChunkManager()
                    .getChunkGenerator().locateStructure(backing,
                            RegistryEntryList.of(holder), probe,
                            Math.max(query.radius(), 1), false);
            if (hit == null || hit.getFirst() == null || seen.contains(hit.getFirst())) {
                continue;
            }
            seen.add(hit.getFirst());
            out.add(location(backing, query.structureId(),
                    new Found(hit.getFirst(), horizontal(probe, hit.getFirst()), holder.value())));
        }
        return out.size() >= limit;
    }

    private static BlockPos centerOf(ServerWorld backing, StructureQuery query) {
        PracticePosition center = query.center();
        if (center == null) {
            return spawnOf(backing);
        }
        return new BlockPos(center.blockX(), center.blockY(), center.blockZ());
    }

    private static BlockPos spawnOf(ServerWorld world) {
        if (world.getLevelProperties() instanceof LevelProperties) {
            return ((LevelProperties) world.getLevelProperties()).getSpawnPos();
        }
        return new BlockPos(0, 64, 0);
    }

    private static long horizontal(BlockPos from, BlockPos to) {
        long dx = (long) to.getX() - from.getX();
        long dz = (long) to.getZ() - from.getZ();
        return (long) Math.sqrt(dx * dx + dz * dz);
    }

    private static StructureLocation location(ServerWorld backing, String structureId, Found best) {
        Map<String, String> metadata = new HashMap<String, String>();
        String bare = FeatureIds121.bare(structureId);
        if ("stronghold".equals(bare)) {
            String portalRoom = findPortalRoom(backing, best.structure, best.pos);
            if (portalRoom != null) {
                metadata.put(StructureLocation.PORTAL_ROOM_KEY, portalRoom);
            }
        } else if ("bastion_remnant".equals(bare)) {
            String type = findBastionType(backing, best.structure, best.pos);
            if (type != null) {
                metadata.put(StructureLocation.BASTION_TYPE_KEY, type);
            }
        }
        return new StructureLocation(structureId,
                new PracticePosition(best.pos.getX(), best.pos.getY(), best.pos.getZ()), metadata);
    }

    /**
     * Resolves the world whose generator can answer the query. Cross-dimension
     * lookups (e.g. blind-travel stronghold measurement from a nether world)
     * use the tracked sibling with the same seed; otherwise the handle's own
     * world answers.
     */
    private ServerWorld generatorWorld(LiveWorld121 handle, String structureId) {
        ServerWorld backing = handle.world();
        PracticeDimension home = FeatureIds121.homeDimension(structureId);
        if (home == handle.dimension()) {
            return backing;
        }
        MinecraftServer server = adapter.server();
        Map<PracticeDimension, LiveWorld121> triple = adapter.triple(backing.getRegistryKey());
        if (server != null && triple != null && triple.get(home) != null
                && server.getWorld(triple.get(home).world().getRegistryKey()) != null) {
            return triple.get(home).world();
        }
        return backing;
    }

    /**
     * Tag-first resolution shared with seed search: family ids name a
     * structure tag on modern versions, so each member is searched and the
     * nearest wins. Ids without a tag resolve directly.
     */
    private static List<RegistryEntry<Structure>> resolveCandidates(MinecraftServer server,
            String id) throws PracticeException {
        Registry<Structure> structures =
                server.getRegistryManager().get(RegistryKeys.STRUCTURE);
        if (structures == null) {
            return Collections.emptyList();
        }
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
            return Collections.emptyList();
        }
        try {
            Optional<RegistryEntry.Reference<Structure>> direct =
                    structures.getEntry(FeatureIds121.key(id));
            if (direct != null && direct.isPresent()) {
                List<RegistryEntry<Structure>> single =
                        new ArrayList<RegistryEntry<Structure>>(1);
                single.add(direct.get());
                return single;
            }
        } catch (RuntimeException bad) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    /**
     * Reads the generated start for a structure in the chunk of a
     * {@code locateStructure} hit. Scans the chunk's recorded starts
     * instead of the structure accessor's position lookup: locate positions
     * carry y=0, which falls outside compact bounding boxes (bastions) and
     * reads back as an invalid start even though the structure generated
     * fine (verified headlessly on 26.3 seed 12345).
     */
    static StructureStart startAt(ServerWorld backing, Structure structure, BlockPos found) {
        Chunk chunk = backing.getChunk(found.getX() >> 4, found.getZ() >> 4);
        for (StructureStart start : chunk.getStructureStarts().values()) {
            if (start != null && start.hasChildren() && start.getStructure() == structure) {
                return start;
            }
        }
        return null;
    }

    private static String findPortalRoom(ServerWorld backing, Structure structure, BlockPos found) {
        try {
            StructureStart start = startAt(backing, structure, found);
            if (start == null) {
                return null;
            }
            for (StructurePiece piece : start.getChildren()) {
                if (piece instanceof StrongholdGenerator.PortalRoom
                        && piece.getBoundingBox() != null) {
                    Vec3i center = piece.getBoundingBox().getCenter();
                    return center.getX() + "," + center.getY() + "," + center.getZ();
                }
            }
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Portal-room scan failed, using stairs: " + bad.getMessage());
        }
        return null;
    }

    /**
     * Reads the bastion subtype from the start piece's template path
     * ({@code bastion/treasure/...}, {@code bastion/bridge/...}...).
     * Best-effort: null when the pieces are not readable.
     */
    private static String findBastionType(ServerWorld backing, Structure structure, BlockPos found) {
        try {
            return BastionTypes121.typeOf(startAt(backing, structure, found));
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Bastion-type scan failed: " + bad.getMessage());
            return null;
        }
    }

    private static final class Found {
        final BlockPos pos;
        final long distance;
        final Structure structure;

        private Found(BlockPos pos, long distance, Structure structure) {
            this.pos = pos;
            this.distance = distance;
            this.structure = structure;
        }
    }
}
