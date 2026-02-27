package com.gregor0410.speedrunpractice.mixin;

import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionType;

public interface DimensionTypeAccess {
    static RegistryKey<DimensionType> getEndType() { return DimensionTypes.THE_END; }
    static RegistryKey<DimensionType> getNetherType() { return DimensionTypes.THE_NETHER; }
}
