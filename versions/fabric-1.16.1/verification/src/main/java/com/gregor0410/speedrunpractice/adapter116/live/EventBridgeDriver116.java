package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import com.gregor0410.speedrunpractice.verification.EventBridgeContractSuite;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verification-only {@link EventBridgeContractSuite.Driver} for 1.16.1.
 * Owns one real {@link EventPoller116} and performs real game-state
 * mutations (block moves, cross-dimension teleports, dragon spawn/kill,
 * structure entry) on the practice worlds the suite starts.
 */
public final class EventBridgeDriver116 implements EventBridgeContractSuite.Driver {
    private final LiveAdapter116 adapter;
    private final PracticeRuntime runtime;
    private final ServerPlayerEntity entity;
    private final LivePlayer player;
    private final EventPoller116 poller;

    public EventBridgeDriver116(LiveAdapter116 adapter, PracticeRuntime runtime,
                                ServerPlayerEntity entity, LivePlayer player) {
        if (adapter == null || runtime == null || entity == null || player == null) {
            throw new IllegalArgumentException("driver collaborators must not be null");
        }
        this.adapter = adapter;
        this.runtime = runtime;
        this.entity = entity;
        this.player = player;
        this.poller = new EventPoller116(adapter);
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
        LiveWorld overworld = requireLiveWorld();
        Map<PracticeDimension, LiveWorld> triple =
                adapter.triple(overworld.world().getRegistryKey());
        if (triple == null || triple.get(PracticeDimension.NETHER) == null) {
            throw new PracticeException("Event-bridge practice has no linked nether",
                    "The practice world has no linked Nether to enter.");
        }
        ServerWorld nether = triple.get(PracticeDimension.NETHER).world();
        entity.teleport(nether, entity.getX(), 80.0, entity.getZ(), entity.yaw, entity.pitch);
    }

    @Override
    public void spawnDragon() throws PracticeException {
        ServerWorld end = requireLiveWorld().world();
        EnderDragonEntity dragon = new EnderDragonEntity(EntityType.ENDER_DRAGON, end);
        // Beside the harness player: its chunk is ticketed, so the spawn is
        // tracked (and visible to the adapter) immediately, with no tick.
        dragon.refreshPositionAndAngles(entity.getX() + 2.0, entity.getY() + 1.0,
                entity.getZ(), 0.0f, 0.0f);
        end.spawnEntity(dragon);
    }

    @Override
    public void killDragon() throws PracticeException {
        PracticeWorld world = runtime.engine().currentContext().world();
        ServerWorld end = requireLiveWorld().world();
        for (EnderDragonEntity dragon : livingDragons(end)) {
            dragon.kill();
        }
        if (adapter.dragons().hasLivingDragon(world)) {
            for (EnderDragonEntity dragon : livingDragons(end)) {
                dragon.remove();
            }
        }
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
        BlockPos target = pieceCenter(requireLiveWorld().world(),
                village.get().position());
        if (target == null) {
            return false;
        }
        ServerWorld backing = requireLiveWorld().world();
        entity.teleport(backing, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                entity.yaw, entity.pitch);
        return true;
    }

    private LiveWorld requireLiveWorld() throws PracticeException {
        PracticeWorld world = runtime.engine().currentContext().world();
        if (!(world instanceof LiveWorld)) {
            throw new PracticeException("Event-bridge practice has no live 1.16.1 world",
                    "The practice world is not a live 1.16.1 world.");
        }
        return (LiveWorld) world;
    }

    private static List<EnderDragonEntity> livingDragons(ServerWorld world) {
        // Loaded entities only: a box query would force-generate every
        // chunk it touches and hang the server thread on a fresh world.
        List<EnderDragonEntity> dragons = new ArrayList<EnderDragonEntity>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof EnderDragonEntity && ((EnderDragonEntity) entity).isAlive()) {
                dragons.add((EnderDragonEntity) entity);
            }
        }
        return dragons;
    }

    /**
     * Center of the first generated village piece in the located chunk
     * (raw structure-accessor scan, the same ground truth the poller
     * reads). Null when the chunk holds no village start.
     */
    private static BlockPos pieceCenter(ServerWorld world, PracticePosition located) {
        Chunk chunk = world.getChunk(located.blockX() >> 4, located.blockZ() >> 4);
        ChunkPos chunkPos = chunk.getPos();
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            StructureStart<?> start;
            try {
                start = world.getStructureAccessor().getStructureStart(
                        ChunkSectionPos.from(chunkPos, sectionY), StructureFeature.VILLAGE, chunk);
            } catch (RuntimeException bad) {
                continue;
            }
            if (start == null || start.getChildren() == null || start.getChildren().isEmpty()) {
                continue;
            }
            for (int i = 0; i < start.getChildren().size(); i++) {
                StructurePiece piece = start.getChildren().get(i);
                if (piece.getBoundingBox() != null) {
                    return new BlockPos(piece.getBoundingBox().getCenter());
                }
            }
        }
        return null;
    }
}
