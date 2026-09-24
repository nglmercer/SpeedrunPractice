package com.gregor0410.speedrunpractice.adapter121.live;

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
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verification-only {@link EventBridgeContractSuite.Driver} for 1.21.1.
 * Owns one real {@link EventPoller121} and performs real game-state
 * mutations (block moves, cross-dimension teleports, dragon spawn/kill,
 * structure entry) on the practice worlds the suite starts.
 */
public final class EventBridgeDriver121 implements EventBridgeContractSuite.Driver {
    private final LiveAdapter121 adapter;
    private final PracticeRuntime runtime;
    private final ServerPlayerEntity entity;
    private final LivePlayer121 player;
    private final EventPoller121 poller;

    public EventBridgeDriver121(LiveAdapter121 adapter, PracticeRuntime runtime,
                                ServerPlayerEntity entity, LivePlayer121 player) {
        if (adapter == null || runtime == null || entity == null || player == null) {
            throw new IllegalArgumentException("driver collaborators must not be null");
        }
        this.adapter = adapter;
        this.runtime = runtime;
        this.entity = entity;
        this.player = player;
        this.poller = new EventPoller121(adapter);
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
        LiveWorld121 overworld = requireLiveWorld();
        Map<PracticeDimension, LiveWorld121> triple =
                adapter.triple(overworld.world().getRegistryKey());
        if (triple == null || triple.get(PracticeDimension.NETHER) == null) {
            throw new PracticeException("Event-bridge practice has no linked nether",
                    "The practice world has no linked Nether to enter.");
        }
        ServerWorld nether = triple.get(PracticeDimension.NETHER).world();
        entity.teleport(nether, entity.getX(), 80.0, entity.getZ(),
                entity.getYaw(), entity.getPitch());
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
                dragon.discard();
            }
        }
    }

    private static List<EnderDragonEntity> livingDragons(ServerWorld world) {
        // Loaded entities only: the by-type lookup misses freshly spawned
        // dragons whose sections are not indexed yet.
        List<EnderDragonEntity> dragons = new ArrayList<EnderDragonEntity>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof EnderDragonEntity && ((EnderDragonEntity) entity).isAlive()) {
                dragons.add((EnderDragonEntity) entity);
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
        BlockPos target = pieceCenter(requireLiveWorld().world(),
                village.get().position());
        if (target == null) {
            return false;
        }
        ServerWorld backing = requireLiveWorld().world();
        entity.teleport(backing, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                entity.getYaw(), entity.getPitch());
        return true;
    }

    private LiveWorld121 requireLiveWorld() throws PracticeException {
        PracticeWorld world = runtime.engine().currentContext().world();
        if (!(world instanceof LiveWorld121)) {
            throw new PracticeException("Event-bridge practice has no live 1.21.1 world",
                    "The practice world is not a live 1.21.1 world.");
        }
        return (LiveWorld121) world;
    }

    /**
     * Center of the first generated village piece in the located chunk
     * (raw chunk-start scan, the same ground truth the poller reads).
     * Null when the chunk holds no village start.
     */
    private BlockPos pieceCenter(ServerWorld backing, PracticePosition located)
            throws PracticeException {
        MinecraftServer server = adapter.server();
        if (server == null) {
            return null;
        }
        Registry<Structure> structures =
                server.getRegistryManager().get(RegistryKeys.STRUCTURE);
        if (structures == null) {
            return null;
        }
        List<RegistryEntry<Structure>> candidates = new ArrayList<RegistryEntry<Structure>>();
        try {
            Optional<RegistryEntryList.Named<Structure>> tag =
                    structures.getEntryList(FeatureIds121.tagKey("village"));
            if (tag != null && tag.isPresent()) {
                for (RegistryEntry<Structure> member : tag.get()) {
                    candidates.add(member);
                }
            }
            if (candidates.isEmpty()) {
                Optional<RegistryEntry.Reference<Structure>> direct =
                        structures.getEntry(FeatureIds121.key("village"));
                if (direct != null && direct.isPresent()) {
                    candidates.add(direct.get());
                }
            }
        } catch (RuntimeException bad) {
            return null;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Chunk chunk = backing.getChunk(located.blockX() >> 4, located.blockZ() >> 4);
        for (StructureStart start : chunk.getStructureStarts().values()) {
            if (start == null || !start.hasChildren()) {
                continue;
            }
            boolean match = false;
            for (RegistryEntry<Structure> candidate : candidates) {
                if (candidate != null && start.getStructure() == candidate.value()) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                continue;
            }
            for (StructurePiece piece : start.getChildren()) {
                if (piece.getBoundingBox() != null) {
                    Vec3i center = piece.getBoundingBox().getCenter();
                    return new BlockPos(center.getX(), center.getY(), center.getZ());
                }
            }
        }
        return null;
    }
}
