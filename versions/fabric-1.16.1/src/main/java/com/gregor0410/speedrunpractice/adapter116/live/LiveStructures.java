package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Structure location on the 1.16.1 chunk generator. Every returned position
 * is a genuine {@code locateStructure} hit — multi-results come from
 * re-querying ringed centers and de-duplicating, never from invented data.
 * Bastion subtype metadata stays empty until chunk-backed type detection
 * lands (the scenarios fall back to random picks meanwhile).
 */
final class LiveStructures implements StructureAdapter {
    private final LiveAdapter116 adapter;

    LiveStructures(LiveAdapter116 adapter) {
        this.adapter = adapter;
    }

    @Override
    public Optional<StructureLocation> locateNearest(PracticeWorld world, StructureQuery query)
            throws PracticeException {
        if (!(world instanceof LiveWorld)) {
            throw new PracticeException("locateNearest got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        StructureFeature<?> feature = FeatureIds.resolve(query.structureId());
        ServerWorld backing = generatorWorld((LiveWorld) world, query.structureId());
        BlockPos center = centerOf(backing, query);
        BlockPos found = backing.getChunkManager().getChunkGenerator()
                .locateStructure(backing, feature, center, query.radius(), false);
        if (found == null) {
            return Optional.empty();
        }
        return Optional.of(location(backing, query.structureId(), found));
    }

    @Override
    public List<StructureLocation> locate(PracticeWorld world, StructureQuery query, int limit)
            throws PracticeException {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
        if (!(world instanceof LiveWorld)) {
            throw new PracticeException("locate got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        List<StructureLocation> out = new ArrayList<StructureLocation>();
        List<BlockPos> seen = new ArrayList<BlockPos>();
        StructureFeature<?> feature = FeatureIds.resolve(query.structureId());
        ServerWorld backing = generatorWorld((LiveWorld) world, query.structureId());
        BlockPos center = centerOf(backing, query);
        int radius = Math.max(query.radius(), 1);
        int[] rings = new int[] {0, radius / 4, radius / 2, (radius * 3) / 4};
        int[][] dirs = new int[][] {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        outer:
        for (int ring : rings) {
            if (ring == 0) {
                if (collect(backing, feature, center, query, out, seen, limit)) {
                    break;
                }
                continue;
            }
            for (int[] dir : dirs) {
                BlockPos probe = center.add(dir[0] * ring, 0, dir[1] * ring);
                if (collect(backing, feature, probe, query, out, seen, limit)) {
                    break outer;
                }
            }
        }
        return out;
    }

    private boolean collect(ServerWorld backing, StructureFeature<?> feature, BlockPos probe,
            StructureQuery query, List<StructureLocation> out, List<BlockPos> seen, int limit) {
        if (out.size() >= limit) {
            return true;
        }
        BlockPos found = backing.getChunkManager().getChunkGenerator()
                .locateStructure(backing, feature, probe, query.radius(), false);
        if (found == null || seen.contains(found)) {
            return false;
        }
        seen.add(found);
        out.add(location(backing, query.structureId(), found));
        return out.size() >= limit;
    }

    private static BlockPos centerOf(ServerWorld backing, StructureQuery query) {
        PracticePosition center = query.center();
        if (center == null) {
            return backing.getSpawnPos();
        }
        return new BlockPos(center.blockX(), center.blockY(), center.blockZ());
    }

    private static StructureLocation location(ServerWorld backing, String structureId, BlockPos found) {
        Map<String, String> metadata = Collections.<String, String>emptyMap();
        if (isStronghold(structureId)) {
            String portalRoom = findPortalRoom(backing, found);
            if (portalRoom != null) {
                metadata = Collections.singletonMap(StructureLocation.PORTAL_ROOM_KEY, portalRoom);
            }
        }
        return new StructureLocation(structureId,
                new PracticePosition(found.getX(), found.getY(), found.getZ()), metadata);
    }

    /**
     * Resolves the world whose generator can answer the query. Cross-dimension
     * lookups (e.g. blind-travel stronghold measurement from a nether world)
     * use the tracked sibling with the same seed; otherwise the handle's own
     * world answers.
     */
    private ServerWorld generatorWorld(LiveWorld handle, String structureId) {
        ServerWorld backing = handle.world();
        PracticeDimension home = FeatureIds.homeDimension(structureId);
        if (home == handle.dimension()) {
            return backing;
        }
        MinecraftServer server = adapter.server();
        Map<PracticeDimension, LiveWorld> triple = adapter.triple(backing.getRegistryKey());
        if (server != null && triple != null && triple.get(home) != null
                && server.getWorld(triple.get(home).world().getRegistryKey()) != null) {
            return triple.get(home).world();
        }
        return backing;
    }

    private static boolean isStronghold(String structureId) {
        return "stronghold".equals(FeatureIds.bare(structureId));
    }

    /**
     * Generates the stronghold chunk and scans its start for the portal-room
     * piece, returning its center as {@code "x,y,z"}. Best-effort: null when
     * the pieces are not readable, in which case scenarios fall back to the
     * stairs position.
     */
    private static String findPortalRoom(ServerWorld backing, BlockPos found) {
        try {
            Chunk chunk = backing.getChunk(found);
            ChunkPos chunkPos = new ChunkPos(found);
            for (int sectionY = 0; sectionY < 16; sectionY++) {
                StructureStart<?> start = backing.getStructureAccessor().getStructureStart(
                        ChunkSectionPos.from(chunkPos, sectionY), StructureFeature.STRONGHOLD, chunk);
                if (start == null || start.getChildren() == null) {
                    continue;
                }
                for (StructurePiece piece : start.getChildren()) {
                    if (piece instanceof net.minecraft.structure.StrongholdGenerator.PortalRoom
                            && piece.getBoundingBox() != null) {
                        Vec3i center = piece.getBoundingBox().getCenter();
                        return center.getX() + "," + center.getY() + "," + center.getZ();
                    }
                }
            }
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Portal-room scan failed, using stairs: " + bad.getMessage());
        }
        return null;
    }
}
