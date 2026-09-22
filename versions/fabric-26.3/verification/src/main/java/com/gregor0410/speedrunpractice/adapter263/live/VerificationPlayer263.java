package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.UUID;

/**
 * Verification-only controlled server player (plan phase 11).
 *
 * <p>Creates a synthetic {@link ServerPlayer} with a loopback connection that
 * never joins a real client. The entity is never added to the player list or
 * any level tracker, so {@link Handle#close()} is a no-op kept for API
 * symmetry with the 1.16.1 harness. Suites use this instead of building ad-hoc
 * fake players so every player-dependent check exercises the real
 * {@code PlayerAdapter}/{@code InventoryAdapter} paths.</p>
 */
public final class VerificationPlayer263 {
    private VerificationPlayer263() {
    }

    /** Creates a harness player positioned at {@code spawn}. Never null. */
    public static Handle create(MinecraftServer server, ServerLevel level,
                                PracticePosition spawn, String name) {
        if (server == null || level == null || spawn == null) {
            throw new IllegalArgumentException("verification player needs a server, level and spawn");
        }
        GameProfile profile = new GameProfile(UUID.randomUUID(),
                name == null ? "verification263" : name);
        ServerPlayer entity = new ServerPlayer(server, level, profile,
                ClientInformation.createDefault());
        entity.connection = new ServerGamePacketListenerImpl(server,
                new Connection(PacketFlow.SERVERBOUND), entity,
                CommonListenerCookie.createInitial(profile, false));
        entity.snapTo(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        return new Handle(entity);
    }

    /** Owned harness player. */
    public static final class Handle implements AutoCloseable {
        private final ServerPlayer entity;
        private final LivePlayer263 player;

        private Handle(ServerPlayer entity) {
            this.entity = entity;
            this.player = new LivePlayer263(entity);
        }

        public ServerPlayer entity() {
            return entity;
        }

        public LivePlayer263 player() {
            return player;
        }

        @Override
        public void close() {
            // The entity never joins the player list or level trackers; nothing to detach.
        }
    }
}
