package com.gregor0410.speedrunpractice.adapter121.mixin;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the dragon fight boss bar so world deletion can clear its players. */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccess121 {
    @Accessor("bossBar")
    ServerBossBar getBossBar();
}
