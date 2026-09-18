package com.gregor0410.speedrunpractice.adapter263.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the fight's boss bar so practice-end deletion can clear its
 * viewers (legacy parity with the 1.16.1 accessor).
 */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccess263 {
    @Accessor("dragonEvent")
    ServerBossEvent getDragonEvent();
}
