package com.gregor0410.speedrunpractice.adapter263.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a jigsaw piece's template reference so the structure adapter can
 * report bastion subtypes ({@code treasure}, {@code bridge},
 * {@code housing}, {@code stables}) from template paths.
 */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccess263 {
    @Accessor("template")
    Either<Identifier, StructureTemplate> getTemplateReference();
}
