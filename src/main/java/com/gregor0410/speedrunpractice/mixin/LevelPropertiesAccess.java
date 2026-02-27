package com.gregor0410.speedrunpractice.mixin;

import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelProperties.class)
public interface LevelPropertiesAccess {
    @Accessor("levelInfo")
    LevelInfo getLevelInfo();
    @Accessor("levelInfo")
    void setLevelInfo(LevelInfo levelInfo);
    @Accessor("generatorOptions")
    GeneratorOptions getGeneratorOptions();
}
