package com.gregor0410.speedrunpractice.adapter116.live;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Verification-only controlled server player (plan phase 11).
 *
 * <p>Creates a synthetic {@link ServerPlayerEntity} that never joins a real
 * client connection, positions it at the practice-world spawn, and removes it
 * from the player list on {@link Handle#close()}. Suites use this instead of
 * building ad-hoc fake players so every player-dependent check exercises the
 * real {@code PlayerAdapter}/{@code InventoryAdapter} paths.</p>
 */
public final class VerificationPlayer116 {
    private VerificationPlayer116() {
    }

    /** Creates a harness player positioned at the given world's spawn. Never null. */
    public static Handle create(MinecraftServer server, LiveWorld world, String name) {
        if (server == null || world == null) {
            throw new IllegalArgumentException("verification player needs a server and world");
        }
        ServerPlayerEntity entity = server.getPlayerManager().createPlayer(
                new GameProfile(UUID.randomUUID(), name == null ? "verification116" : name));
        entity.networkHandler = new ServerPlayNetworkHandler(server,
                new ClientConnection(NetworkSide.SERVERBOUND), entity);
        server.getPlayerManager().getPlayerList().add(entity);
        entity.refreshPositionAndAngles(world.world().getSpawnPos(), 90.0F, 0.0F);
        return new Handle(server, entity);
    }

    /** Owned harness player; {@link #close()} detaches it from the server. */
    public static final class Handle implements AutoCloseable {
        private final MinecraftServer server;
        private final ServerPlayerEntity entity;
        private final LivePlayer player;

        private Handle(MinecraftServer server, ServerPlayerEntity entity) {
            this.server = server;
            this.entity = entity;
            this.player = new LivePlayer(entity);
        }

        public ServerPlayerEntity entity() {
            return entity;
        }

        public LivePlayer player() {
            return player;
        }

        @Override
        public void close() {
            server.getPlayerManager().getPlayerList().remove(entity);
        }
    }
}
