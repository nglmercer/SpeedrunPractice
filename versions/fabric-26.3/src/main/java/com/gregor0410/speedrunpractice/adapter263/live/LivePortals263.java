package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.adapter.PortalAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;

import java.util.Optional;

/**
 * Nether portal creation on the 26.3 portal forcer: vanilla searches for a
 * suitable spot near the requested position and builds the frame, and the
 * portal mixins keep travel inside the practice triple. The
 * overworld-side call also pins the portal chunk so the frame always lands,
 * even far from loaded terrain.
 */
final class LivePortals263 implements PortalAdapter {
    private final LiveAdapter263 adapter;

    LivePortals263(LiveAdapter263 adapter) {
        this.adapter = adapter;
    }

    @Override
    public void createNetherPortal(PracticeWorld world, PracticePosition position) throws PracticeException {
        requireHandle(world, "createNetherPortal");
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            throw new PracticeException("createNetherPortal with no running server",
                    "Cannot build the portal: no world is loaded. Open a single-player world first.");
        }
        ServerLevel backing = LiveAdapter263.requireLevel(adapter, world, "createNetherPortal");
        BlockPos portalPos = new BlockPos(position.blockX(), position.blockY(), position.blockZ());
        backing.getChunk(portalPos);
        Optional<BlockUtil.FoundRectangle> built =
                backing.getPortalForcer().createPortal(portalPos, Direction.Axis.X);
        if (!built.isPresent()) {
            throw new PracticeException("createNetherPortal found no room at " + portalPos,
                    "No room for a portal there. Pick a flatter spot and try again.");
        }
    }

    @Override
    public void linkPortals(PracticeWorld overworld, PracticeWorld nether, PracticePosition overworldPos)
            throws PracticeException {
        requireHandle(overworld, "linkPortals");
        requireHandle(nether, "linkPortals");
        if (overworldPos == null) {
            throw new IllegalArgumentException("overworldPos must not be null");
        }
        MinecraftServer server = adapter.server();
        if (server == null) {
            throw new PracticeException("linkPortals with no running server",
                    "Cannot link portals: no world is loaded. Open a single-player world first.");
        }
        LiveAdapter263.requireLevel(adapter, overworld, "linkPortals");
        LiveAdapter263.requireLevel(adapter, nether, "linkPortals");
        createNetherPortal(overworld, overworldPos);
        PracticePosition netherPos = new PracticePosition(overworldPos.x() / 8.0, overworldPos.y(),
                overworldPos.z() / 8.0, overworldPos.yaw(), overworldPos.pitch());
        createNetherPortal(nether, netherPos);
    }

    private static LiveWorld263 requireHandle(PracticeWorld world, String operation) throws PracticeException {
        if (!(world instanceof LiveWorld263)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        return (LiveWorld263) world;
    }
}
