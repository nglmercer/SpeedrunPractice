package com.gregor0410.speedrunpractice.mixin;

import com.gregor0410.speedrunpractice.world.PracticeWorld;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {
    @Redirect(method="onEntityCollision",at=@At(value="INVOKE",target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"))
    private RegistryKey<World> resolveEndPortalTargetWorld(World world){
        return world.getRegistryKey();
    }
}
