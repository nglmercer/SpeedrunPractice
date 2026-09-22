package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * Verification-only controlled server player (plan phase 11).
 *
 * <p>Creates a synthetic {@link ServerPlayerEntity} with a loopback network
 * handler that never joins a real client connection. The entity is never added
 * to the player list or any world tracker, so {@link Handle#close()} is a
 * no-op kept for API symmetry with the 1.16.1 harness. Suites use this instead
 * of building ad-hoc fake players so every player-dependent check exercises
 * the real {@code PlayerAdapter}/{@code InventoryAdapter} paths.</p>
 */
public final class VerificationPlayer121 {
    private VerificationPlayer121() {
    }

    /** Creates a harness player positioned at {@code spawn}. Never null. */
    public static Handle create(MinecraftServer server, ServerWorld world,
                                PracticePosition spawn, String name) {
        if (server == null || world == null || spawn == null) {
            throw new IllegalArgumentException("verification player needs a server, world and spawn");
        }
        GameProfile profile = new GameProfile(UUID.randomUUID(),
                name == null ? "verification121" : name);
        ServerPlayerEntity entity = new ServerPlayerEntity(server, world, profile,
                SyncedClientOptions.createDefault());
        entity.networkHandler = new ServerPlayNetworkHandler(server,
                new ClientConnection(NetworkSide.SERVERBOUND), entity,
                ConnectedClientData.createDefault(profile, false));
        entity.refreshPositionAndAngles(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        return new Handle(entity);
    }

    /** Owned harness player. */
    public static final class Handle implements AutoCloseable {
        private final ServerPlayerEntity entity;
        private final LivePlayer121 player;

        private Handle(ServerPlayerEntity entity) {
            this.entity = entity;
            this.player = new LivePlayer121(entity);
        }

        public ServerPlayerEntity entity() {
            return entity;
        }

        public LivePlayer121 player() {
            return player;
        }

        @Override
        public void close() {
            // The entity never joins the player list or world trackers; nothing to detach.
        }
    }
}
