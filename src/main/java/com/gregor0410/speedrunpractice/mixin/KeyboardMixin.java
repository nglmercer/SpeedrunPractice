package com.gregor0410.speedrunpractice.mixin;

import net.minecraft.text.Text;
import net.minecraft.client.Keyboard;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Redirect(method="processF3",at=@At(value="INVOKE",target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"))
    private RegistryKey<World> resolveF3AndCWorld(World world){
        return world.getRegistryKey();
    }
}
