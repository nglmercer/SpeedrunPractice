package com.gregor0410.speedrunpractice.mixin;

import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Version-local access to the server-side saturation field on 1.16.1. */
@Mixin(HungerManager.class)
public interface HungerManagerAccess {
    @Accessor("foodSaturationLevel")
    void speedrunPractice$setFoodSaturationLevel(float value);
}
