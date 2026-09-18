package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.adapter.PortalAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nether portal linkage on the legacy portal helpers: the linked pair goes
 * through {@code Practice.createPortals} with the tracked triple, and the
 * single-sided call replays the same portal-forcer steps for one world.
 */
final class LivePortals implements PortalAdapter {
    private final LiveAdapter116 adapter;

    LivePortals(LiveAdapter116 adapter) {
        this.adapter = adapter;
    }

    @Override
    public void createNetherPortal(PracticeWorld world, PracticePosition position) throws PracticeException {
        LiveWorld handle = requireHandle(world, "createNetherPortal");
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            throw new PracticeException("createNetherPortal with no running server",
                    "Cannot build the portal: no world is loaded. Open a single-player world first.");
        }
        ServerWorld backing = LiveAdapter116.requireWorld(adapter, world, "createNetherPortal");
        ServerPlayerEntity player = singlePlayer(server, "createNetherPortal");
        BlockPos prevPos = player.getBlockPos();
        BlockPos portalPos = new BlockPos(position.blockX(), position.blockY(), position.blockZ());
        try {
            player.refreshPositionAndAngles(portalPos, 90, 0);
            player.setInNetherPortal(portalPos);
            backing.getPortalForcer().createPortal(player);
            // field_19280 is the unmapped PORTAL ticket in this yarn build (legacy parity).
            backing.getChunkManager().addTicket(ChunkTicketType.field_19280, new ChunkPos(portalPos), 3, portalPos);
        } finally {
            player.refreshPositionAndAngles(prevPos, 90, 0);
            player.netherPortalCooldown = player.getDefaultNetherPortalCooldown();
        }
    }

    @Override
    public void linkPortals(PracticeWorld overworld, PracticeWorld nether, PracticePosition overworldPos)
            throws PracticeException {
        LiveWorld overHandle = requireHandle(overworld, "linkPortals");
        LiveWorld netherHandle = requireHandle(nether, "linkPortals");
        if (overworldPos == null) {
            throw new IllegalArgumentException("overworldPos must not be null");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            throw new PracticeException("linkPortals with no running server",
                    "Cannot link portals: no world is loaded. Open a single-player world first.");
        }
        LiveAdapter116.requireWorld(adapter, overworld, "linkPortals");
        LiveAdapter116.requireWorld(adapter, nether, "linkPortals");
        ServerPlayerEntity player = singlePlayer(server, "linkPortals");
        Map<RegistryKey<DimensionType>, com.gregor0410.speedrunpractice.world.PracticeWorld> linked =
                new HashMap<RegistryKey<DimensionType>,
                        com.gregor0410.speedrunpractice.world.PracticeWorld>();
        linked.put(DimensionType.OVERWORLD_REGISTRY_KEY, legacy(overHandle));
        linked.put(DimensionType.THE_NETHER_REGISTRY_KEY, legacy(netherHandle));
        ServerWorld overworldBacking = overHandle.world();
        com.gregor0410.speedrunpractice.command.Practice.createPortals(linked, player, overworldBacking,
                new BlockPos(overworldPos.blockX(), overworldPos.blockY(), overworldPos.blockZ()));
    }

    private static com.gregor0410.speedrunpractice.world.PracticeWorld legacy(LiveWorld handle)
            throws PracticeException {
        if (!(handle.world() instanceof com.gregor0410.speedrunpractice.world.PracticeWorld)) {
            throw new PracticeException("Portal linkage needs practice worlds",
                    "Portals can only link practice worlds. Stop and start the practice again.");
        }
        return (com.gregor0410.speedrunpractice.world.PracticeWorld) handle.world();
    }

    private static ServerPlayerEntity singlePlayer(MinecraftServer server, String operation)
            throws PracticeException {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            throw new PracticeException(operation + " with no player online",
                    "Cannot build the portal: nobody is in the world.");
        }
        return players.get(0);
    }

    private static LiveWorld requireHandle(PracticeWorld world, String operation) throws PracticeException {
        if (!(world instanceof LiveWorld)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        return (LiveWorld) world;
    }
}
