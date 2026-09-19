package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Live 1.21.1 player handle. Created fresh per command invocation from the
 * invocation's {@link ServerPlayerEntity}; shared code only sees the id.
 */
public final class LivePlayer121 implements PracticePlayer {
    private final ServerPlayerEntity player;

    public LivePlayer121(ServerPlayerEntity player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        this.player = player;
    }

    public ServerPlayerEntity entity() {
        return player;
    }

    @Override
    public String handleId() {
        return player.getUuid().toString();
    }
}
