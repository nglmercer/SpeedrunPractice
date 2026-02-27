package com.gregor0410.speedrunpractice.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerEntity.class)
public interface ServerPlayerEntityAccess {
    @Accessor("spawnPointDimension")
    void setSpawnPointDimension(RegistryKey<World> dimension);
    @Accessor("spawnPointPosition")
    void setSpawnPointPosition(@Nullable BlockPos pos);
    @Accessor("seenCredits")
    void setSeenCredits(boolean value);
    @Accessor("enteredNetherPos")
    void setEnteredNetherPos(Vec3d netherPos);
}
