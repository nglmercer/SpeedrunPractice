package com.gregor0410.speedrunpractice.adapter121.mixin;

import com.gregor0410.speedrunpractice.adapter121.world.PracticeWorld121;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.GeneratorOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Seeds the practice world's structure locator with the practice seed.
 * {@code ServerWorld} builds its {@code StructureLocator} (plus the dragon
 * fight and random sequences) from the server-global {@code GeneratorOptions}
 * seed, bypassing the practice world's {@code getSeed()} override; every
 * other consumer (structure state, set loop, chunk random) already sees the
 * practice seed. Without this redirect the live presence gate disagrees with
 * live chunk generation on practice worlds, so {@code /locate}-style
 * searches skip structures that would generate. Only active while a practice
 * world is under construction; every other world keeps the global seed.
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldSeedMixin121 {
    @Redirect(method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/gen/GeneratorOptions;getSeed()J"))
    private long practiceSeedForWorldConstruction(GeneratorOptions options) {
        Long constructing = PracticeWorld121.constructingSeed();
        return constructing != null ? constructing.longValue() : options.getSeed();
    }
}
