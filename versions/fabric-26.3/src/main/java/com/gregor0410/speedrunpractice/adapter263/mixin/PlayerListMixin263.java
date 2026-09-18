package com.gregor0410.speedrunpractice.adapter263.mixin;

import com.gregor0410.speedrunpractice.adapter263.world.PracticeLevel263;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps death respawns inside the practice triple (mirrors the 1.16.1
 * player-manager mixin). Dying in a practice level with a host-world
 * respawn point (never slept in practice) would otherwise strand the
 * player in the host save; the respawn config is rewritten to the
 * practice overworld spawn instead. Beds and anchors inside practice
 * worlds already point at practice keys and are left alone, as are
 * single end worlds, which respawn on their own platform.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin263 {
    @Inject(method = "respawn", at = @At("HEAD"))
    private void keepRespawnInPractice(ServerPlayer player, boolean keepInventory,
            Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayer> info) {
        if (!(player.level() instanceof PracticeLevel263)) {
            return;
        }
        PracticeLevel263 level = (PracticeLevel263) player.level();
        ServerPlayer.RespawnConfig config = player.getRespawnConfig();
        ResourceKey<Level> respawnDim =
                config == null || config.respawnData() == null ? null : config.respawnData().dimension();
        if (respawnDim != null && isPracticeKey(respawnDim)) {
            return;
        }
        ServerLevel overworld = level.associatedLevel(Level.OVERWORLD);
        if (overworld != null) {
            BlockPos spawn = overworld.getRespawnData().pos();
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                    LevelData.RespawnData.of(overworld.dimension(), spawn, 90.0f, 0.0f), false), false);
        } else {
            BlockPos platform = ServerLevel.END_SPAWN_POINT;
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                    LevelData.RespawnData.of(level.dimension(), platform, 90.0f, 0.0f), false), false);
        }
    }

    private static boolean isPracticeKey(ResourceKey<Level> key) {
        return key != null && "speedrun_practice".equals(key.identifier().getNamespace());
    }
}
