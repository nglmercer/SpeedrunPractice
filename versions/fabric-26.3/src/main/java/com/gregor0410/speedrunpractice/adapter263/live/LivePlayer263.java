package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Live 26.3 player handle. Created fresh per command invocation from the
 * invocation's {@link ServerPlayer}; shared code only sees the id.
 */
public final class LivePlayer263 implements PracticePlayer {
    private final ServerPlayer player;

    public LivePlayer263(ServerPlayer player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        this.player = player;
    }

    public ServerPlayer entity() {
        return player;
    }

    @Override
    public String handleId() {
        return player.getUUID().toString();
    }
}
