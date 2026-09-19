package com.gregor0410.speedrunpractice.adapter121.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the pool element's template reference for bastion typing. */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccess121 {
    @Accessor("location")
    Either<Identifier, StructureTemplate> getLocation();
}
