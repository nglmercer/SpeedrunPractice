package com.gregor0410.speedrunpractice.mixin;

import net.minecraft.text.Text;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DebugHud.class)
public class DebugHudMixin {
    @Redirect(method="getLeftText",at=@At(value="INVOKE",target="Lnet/minecraft/client/world/ClientWorld;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"))
    private RegistryKey<World> resolveRegistryKey(ClientWorld world){
        return world.getRegistryKey();
    }
}
