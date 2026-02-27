package com.gregor0410.speedrunpractice.mixin;

import com.gregor0410.speedrunpractice.SpeedrunPractice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin {
    @Inject(method="init",at=@At("TAIL"))
    public void addConfigButton(CallbackInfo ci){
        ((ScreenAccess)this).invokeAddDrawableChild(ButtonWidget.builder(Text.translatable("speedrun-practice.options"),(buttonWidget)->{
            MinecraftClient.getInstance().setScreen(SpeedrunPractice.config.getScreen(((Screen)(Object)this)));
        }).dimensions(((Screen)(Object)this).width/2 + 5,((Screen)(Object)this).height / 6 + 138,150,20).build());
    }
}
