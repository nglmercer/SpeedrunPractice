package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.adapter263.mixin.SinglePoolElementAccess263;
import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Structure location on the 26.3 chunk generator. Every returned position
 * is a genuine {@code findNearestMapStructure} hit — multi-results come
 * from re-querying ringed centers and de-duplicating, never from invented
 * data. Family ids ({@code village}, {@code mineshaft}...) resolve through
 * the structure tag first, matching seed search; each member is queried and
 * the nearest wins.
 *
 * <p>Metadata: strongholds carry the portal-room center
 * ({@code portal_room} as {@code "x,y,z"}) from the generated start, and
 * bastions carry their subtype ({@code bastion_type} as {@code treasure},
 * {@code bridge}, {@code housing} or {@code stables}) from the start
 * piece's template. Both degrade gracefully when the pieces are unreadable.
 */
final class LiveStructures263 implements StructureAdapter {
    private final LiveAdapter263 adapter;

    LiveStructures263(LiveAdapter263 adapter) {
        this.adapter = adapter;
    }

    @Override
    public Optional<StructureLocation> locateNearest(PracticeWorld world, StructureQuery query)
            throws PracticeException {
        if (!(world instanceof LiveWorld263)) {
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
        List<Holder<Structure>> candidates = resolveCandidates(server, query.structureId());
        if (candidates.isEmpty()) {
            throw new PracticeException("Unknown structure \"" + query.structureId() + "\"",
                    "Unknown structure \"" + query.structureId() + "\". Try village, shipwreck,"
                            + " buried_treasure, ruined_portal, bastion_remnant, fortress or stronghold.");
        }
        ServerLevel backing = generatorLevel((LiveWorld263) world, query.structureId());
        BlockPos center = centerOf(backing, query);
        Found best = null;
        for (Holder<Structure> holder : candidates) {
            Pair<BlockPos, Holder<Structure>> hit = backing.getChunkSource().getGenerator()
                    .findNearestMapStructure(backing, HolderSet.direct(holder),
                            center, Math.max(query.radius(), 1), false);
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
        if (!(world instanceof LiveWorld263)) {
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
        List<Holder<Structure>> candidates = resolveCandidates(server, query.structureId());
        if (candidates.isEmpty()) {
            throw new PracticeException("Unknown structure \"" + query.structureId() + "\"",
                    "Unknown structure \"" + query.structureId() + "\". Try village, shipwreck,"
                            + " buried_treasure, ruined_portal, bastion_remnant, fortress or stronghold.");
        }
        List<StructureLocation> out = new ArrayList<StructureLocation>();
        List<BlockPos> seen = new ArrayList<BlockPos>();
        ServerLevel backing = generatorLevel((LiveWorld263) world, query.structureId());
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
                BlockPos probe = center.offset(dir[0] * ring, 0, dir[1] * ring);
                if (collect(backing, candidates, probe, query, out, seen, limit)) {
                    break outer;
                }
            }
        }
        return out;
    }

    private boolean collect(ServerLevel backing, List<Holder<Structure>> candidates, BlockPos probe,
            StructureQuery query, List<StructureLocation> out, List<BlockPos> seen, int limit) {
        if (out.size() >= limit) {
            return true;
        }
        for (Holder<Structure> holder : candidates) {
            if (out.size() >= limit) {
                return true;
            }
            Pair<BlockPos, Holder<Structure>> hit = backing.getChunkSource().getGenerator()
                    .findNearestMapStructure(backing, HolderSet.direct(holder),
                            probe, Math.max(query.radius(), 1), false);
            if (hit == null || hit.getFirst() == null || seen.contains(hit.getFirst())) {
                continue;
            }
            seen.add(hit.getFirst());
            out.add(location(backing, query.structureId(),
                    new Found(hit.getFirst(), horizontal(probe, hit.getFirst()), holder.value())));
        }
        return out.size() >= limit;
    }

    private static BlockPos centerOf(ServerLevel backing, StructureQuery query) {
        PracticePosition center = query.center();
        if (center == null) {
            return backing.getRespawnData().pos();
        }
        return new BlockPos(center.blockX(), center.blockY(), center.blockZ());
    }

    private static long horizontal(BlockPos from, BlockPos to) {
        long dx = (long) to.getX() - from.getX();
        long dz = (long) to.getZ() - from.getZ();
        return (long) Math.sqrt(dx * dx + dz * dz);
    }

    private static StructureLocation location(ServerLevel backing, String structureId, Found best) {
        Map<String, String> metadata = new HashMap<String, String>();
        String bare = FeatureIds263.bare(structureId).toLowerCase();
        if ("stronghold".equals(bare)) {
            String portalRoom = findPortalRoom(backing, best.structure, best.pos);
            if (portalRoom != null) {
                metadata.put("portal_room", portalRoom);
            }
        } else if ("bastion_remnant".equals(bare)) {
            String type = findBastionType(backing, best.structure, best.pos);
            if (type != null) {
                metadata.put("bastion_type", type);
            }
        }
        return new StructureLocation(structureId,
                new PracticePosition(best.pos.getX(), best.pos.getY(), best.pos.getZ()), metadata);
    }

    /**
     * Resolves the level whose generator can answer the query. Cross-dimension
     * lookups (e.g. blind-travel stronghold measurement from a nether world)
     * use the tracked sibling with the same seed; otherwise the handle's own
     * level answers.
     */
    private ServerLevel generatorLevel(LiveWorld263 handle, String structureId) {
        ServerLevel backing = handle.level();
        PracticeDimension home = FeatureIds263.homeDimension(structureId);
        if (home == handle.dimension()) {
            return backing;
        }
        MinecraftServer server = adapter.server();
        Map<PracticeDimension, LiveWorld263> triple = adapter.triple(backing.dimension());
        if (server != null && triple != null && triple.get(home) != null
                && server.getLevel(triple.get(home).level().dimension()) != null) {
            return triple.get(home).level();
        }
        return backing;
    }

    /**
     * Tag-first resolution shared with seed search: family ids name a
     * structure tag on modern versions, so each member is searched and the
     * nearest wins. Ids without a tag resolve directly.
     */
    private static List<Holder<Structure>> resolveCandidates(MinecraftServer server, String id)
            throws PracticeException {
        RegistryAccess registries = server.registryAccess();
        HolderLookup<Structure> structures;
        try {
            structures = registries.lookupOrThrow(Registries.STRUCTURE);
        } catch (RuntimeException missing) {
            return Collections.emptyList();
        }
        Optional<HolderSet.Named<Structure>> tag = structures.get(FeatureIds263.tagKey(id));
        if (tag.isPresent() && tag.get().size() > 0) {
            List<Holder<Structure>> members = new ArrayList<Holder<Structure>>(tag.get().size());
            for (Holder<Structure> member : tag.get()) {
                members.add(member);
            }
            return members;
        }
        Optional<Holder.Reference<Structure>> direct = structures.get(FeatureIds263.key(id));
        if (direct.isPresent()) {
            List<Holder<Structure>> single = new ArrayList<Holder<Structure>>(1);
            single.add(direct.get());
            return single;
        }
        return Collections.emptyList();
    }

    /**
     * Loads the stronghold chunk and scans its start for the portal-room
     * piece, returning its center as {@code "x,y,z"}. Best-effort: null
     * when the pieces are not readable, in which case scenarios fall back
     * to the stairs position.
     */
    private static String findPortalRoom(ServerLevel backing, Structure structure, BlockPos found) {
        try {
            backing.getChunk(found);
            StructureStart start = backing.structureManager().getStructureAt(found, structure);
            if (start == null || !start.isValid()) {
                return null;
            }
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof StrongholdPieces.PortalRoom && piece.getBoundingBox() != null) {
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
    private static String findBastionType(ServerLevel backing, Structure structure, BlockPos found) {
        try {
            backing.getChunk(found);
            StructureStart start = backing.structureManager().getStructureAt(found, structure);
            if (start == null || !start.isValid() || start.getPieces().isEmpty()) {
                return null;
            }
            StructurePiece first = start.getPieces().get(0);
            if (!(first instanceof PoolElementStructurePiece)) {
                return null;
            }
            StructurePoolElement element = ((PoolElementStructurePiece) first).getElement();
            if (!(element instanceof SinglePoolElement)) {
                return null;
            }
            Either<Identifier, ?> template =
                    ((SinglePoolElementAccess263) (Object) element).getTemplateReference();
            if (template == null) {
                return null;
            }
            Optional<Identifier> left = template.left();
            if (!left.isPresent()) {
                return null;
            }
            String path = left.get().getPath();
            if (!path.startsWith("bastion/")) {
                return null;
            }
            String[] parts = path.split("/");
            if (parts.length < 2) {
                return null;
            }
            String type = parts[1];
            if ("treasure".equals(type) || "bridge".equals(type) || "housing".equals(type)
                    || "stables".equals(type) || "mobs".equals(type)) {
                return "mobs".equals(type) ? null : type;
            }
            return null;
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
