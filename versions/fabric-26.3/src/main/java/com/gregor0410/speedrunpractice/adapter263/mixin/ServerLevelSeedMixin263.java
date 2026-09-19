package com.gregor0410.speedrunpractice.adapter263.mixin;

import com.gregor0410.speedrunpractice.adapter263.world.PracticeLevel263;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Seeds the practice level's structure gate with the practice seed.
 * {@code ServerLevel} builds its {@code StructureCheck} (and the end dragon
 * fight) from the server-global {@code WorldOptions} seed, bypassing the
 * practice level's {@code getSeed()} override; every other consumer
 * (structure state, set loop, chunk random) already sees the practice seed.
 * Without this redirect the live presence gate disagrees with live chunk
 * generation on practice worlds, so {@code /locate}-style searches skip
 * structures that would generate. Only active while a practice level is
 * under construction; every other level keeps the global seed.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSeedMixin263 {
    @Redirect(method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long practiceSeedForLevelConstruction(WorldOptions options) {
        Long constructing = PracticeLevel263.constructingSeed();
        return constructing != null ? constructing.longValue() : options.seed();
    }
}
