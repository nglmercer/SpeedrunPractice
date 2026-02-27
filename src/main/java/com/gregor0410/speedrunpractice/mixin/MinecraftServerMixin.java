package com.gregor0410.speedrunpractice.mixin;

import com.gregor0410.speedrunpractice.IMinecraftServer;
import com.gregor0410.speedrunpractice.world.PracticeWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements IMinecraftServer {
    @Shadow @Final protected LevelStorage.Session session;
    @Shadow @Final private Map<RegistryKey<World>, ServerWorld> worlds;
    @Shadow public abstract ServerWorld getOverworld();
    @Shadow public abstract PlayerManager getPlayerManager();
    @Shadow public abstract boolean save(boolean bl, boolean bl2, boolean bl3);

    private final List<PracticeWorld> endPracticeWorlds = new ArrayList<>();

    @Override
    public ServerWorld createEndPracticeWorld(long seed) throws IOException {
        return null;
    }

    @Inject(method="tick",at=@At("HEAD"))
    private void onTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci){
    }

    @Inject(method="setDifficulty",at=@At("HEAD"))
    private void setDifficulty(Difficulty difficulty, boolean bl, CallbackInfo ci){
    }

    @Override
    public Map<RegistryKey<DimensionType>, PracticeWorld> createLinkedPracticeWorld(long seed) throws IOException {
        return new HashMap<>();
    }

    public List<PracticeWorld> getEndPracticeWorlds() {
        return endPracticeWorlds;
    }

    @Inject(method="shutdown",at=@At("HEAD"))
    private void removePracticeWorlds(CallbackInfo ci) throws IOException {
        for (ServerPlayerEntity player : this.getPlayerManager().getPlayerList()) {
            if (Objects.equals(player.getSpawnPointDimension().getValue().getNamespace(), "speedrun_practice")) {
                player.setSpawnPoint(World.OVERWORLD, null, 0.0f, false, false);
                this.getPlayerManager().respawnPlayer(player, true, net.minecraft.entity.Entity.RemovalReason.CHANGED_DIMENSION);
            }
        }
    }
}
