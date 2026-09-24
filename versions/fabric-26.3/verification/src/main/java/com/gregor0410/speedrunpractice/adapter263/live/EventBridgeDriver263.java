package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import com.gregor0410.speedrunpractice.verification.EventBridgeContractSuite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verification-only {@link EventBridgeContractSuite.Driver} for 26.3.
 * Owns one real {@link EventPoller263} and performs real game-state
 * mutations (block moves, cross-dimension teleports, dragon spawn/kill,
 * structure entry) on the practice worlds the suite starts.
 */
public final class EventBridgeDriver263 implements EventBridgeContractSuite.Driver {
    private final LiveAdapter263 adapter;
    private final PracticeRuntime runtime;
    private final ServerPlayer entity;
    private final LivePlayer263 player;
    private final EventPoller263 poller;

    public EventBridgeDriver263(LiveAdapter263 adapter, PracticeRuntime runtime,
                                ServerPlayer entity, LivePlayer263 player) {
        if (adapter == null || runtime == null || entity == null || player == null) {
            throw new IllegalArgumentException("driver collaborators must not be null");
        }
        this.adapter = adapter;
        this.runtime = runtime;
        this.entity = entity;
        this.player = player;
        this.poller = new EventPoller263(adapter);
    }

    @Override
    public PracticeRuntime runtime() {
        return runtime;
    }

    @Override
    public PracticePlayer player() {
        return player;
    }

    @Override
    public void poll() {
        poller.poll(runtime);
    }

    @Override
    public void movePlayer(double dx, double dz) throws PracticeException {
        PracticePosition current = adapter.players().getPosition(player);
        adapter.players().teleport(player, new PracticePosition(
                current.x() + dx, current.y(), current.z() + dz,
                current.yaw(), current.pitch()));
    }

    @Override
    public void enterNether() throws PracticeException {
        LiveWorld263 overworld = requireLiveWorld();
        Map<PracticeDimension, LiveWorld263> triple =
                adapter.triple(overworld.level().dimension());
        if (triple == null || triple.get(PracticeDimension.NETHER) == null) {
            throw new PracticeException("Event-bridge practice has no linked nether",
                    "The practice world has no linked Nether to enter.");
        }
        ServerLevel nether = triple.get(PracticeDimension.NETHER).level();
        entity.teleport(new TeleportTransition(nether,
                new Vec3(entity.getX(), 80.0, entity.getZ()),
                Vec3.ZERO, entity.getYRot(), entity.getXRot(), TeleportTransition.DO_NOTHING));
    }

    @Override
    public void spawnDragon() throws PracticeException {
        ServerLevel end = requireLiveWorld().level();
        EnderDragon dragon = new EnderDragon(EntityTypes.ENDER_DRAGON, end);
        // Beside the harness player: its chunk is ticketed, so the spawn is
        // tracked (and visible to the adapter) immediately, with no tick.
        dragon.snapTo(entity.getX() + 2.0, entity.getY() + 1.0, entity.getZ(), 0.0f, 0.0f);
        end.addFreshEntity(dragon);
    }

    @Override
    public void killDragon() throws PracticeException {
        PracticeWorld world = runtime.engine().currentContext().world();
        ServerLevel end = requireLiveWorld().level();
        for (EnderDragon dragon : livingDragons(end)) {
            dragon.kill(end);
        }
        if (adapter.dragons().hasLivingDragon(world)) {
            for (EnderDragon dragon : livingDragons(end)) {
                dragon.discard();
            }
        }
    }

    private static List<EnderDragon> livingDragons(ServerLevel level) {
        // Loaded entities only: the by-type lookup misses freshly spawned
        // dragons whose sections are not indexed yet.
        List<EnderDragon> dragons = new ArrayList<EnderDragon>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EnderDragon && entity.isAlive()) {
                dragons.add((EnderDragon) entity);
            }
        }
        return dragons;
    }

    @Override
    public boolean hasLivingDragon() throws PracticeException {
        return adapter.dragons().hasLivingDragon(runtime.engine().currentContext().world());
    }

    @Override
    public boolean enterPolledStructure() throws PracticeException {
        PracticeWorld world = runtime.engine().currentContext().world();
        Optional<StructureAdapter.StructureLocation> village = adapter.structures()
                .locateNearest(world, StructureAdapter.StructureQuery.builder("village")
                        .radius(5000).build());
        if (!village.isPresent()) {
            return false;
        }
        ServerLevel backing = requireLiveWorld().level();
        BlockPos inside = insidePosition(backing, village.get().position());
        if (inside == null) {
            return false;
        }
        entity.teleport(new TeleportTransition(backing,
                new Vec3(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5),
                Vec3.ZERO, entity.getYRot(), entity.getXRot(), TeleportTransition.DO_NOTHING));
        return true;
    }

    private LiveWorld263 requireLiveWorld() throws PracticeException {
        PracticeWorld world = runtime.engine().currentContext().world();
        if (!(world instanceof LiveWorld263)) {
            throw new PracticeException("Event-bridge practice has no live 26.3 world",
                    "The practice world is not a live 26.3 world.");
        }
        return (LiveWorld263) world;
    }

    /**
     * A position inside the located village, found by descending from the
     * surface through the level's own structure occupancy (the same
     * ground truth the poller reads). The located column itself rarely
     * holds a piece, so a small grid around it is probed, center first.
     * Null when no village piece is found.
     */
    private static final int[] PROBE_OFFSETS = new int[]{0, -8, 8, -16, 16, -24, 24, -32, 32};

    private static BlockPos insidePosition(ServerLevel level, PracticePosition located) {
        TagKey<Structure> village;
        try {
            village = FeatureIds263.tagKey("village");
        } catch (Exception bad) {
            return null;
        }
        for (int dx : PROBE_OFFSETS) {
            for (int dz : PROBE_OFFSETS) {
                int x = located.blockX() + dx;
                int z = located.blockZ() + dz;
                try {
                    // Full generation first: height and structure lookups on
                    // an ungenerated column read empty primer data.
                    level.getChunk(x >> 4, z >> 4);
                } catch (RuntimeException bad) {
                    continue;
                }
                int surface;
                try {
                    surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                } catch (RuntimeException bad) {
                    continue;
                }
                for (int y = surface + 4; y > surface - 32 && y > level.getMinY(); y -= 2) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    StructureStart start;
                    try {
                        start = level.structureManager().getStructureWithPieceAt(candidate, village);
                    } catch (RuntimeException bad) {
                        break;
                    }
                    if (start != null && start.isValid()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }
}
