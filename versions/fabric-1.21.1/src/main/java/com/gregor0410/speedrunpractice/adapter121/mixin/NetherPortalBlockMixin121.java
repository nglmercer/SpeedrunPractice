package com.gregor0410.speedrunpractice.adapter121.mixin;

import com.gregor0410.speedrunpractice.adapter121.world.PracticeWorld121;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps nether-portal travel inside the practice triple. Practice worlds
 * use {@code speedrun_practice} keys, so two redirects make the vanilla
 * destination choice triple-aware: worlds report their vanilla key (correct
 * nether/overworld branch and coordinate scaling), and the destination
 * lookup resolves the sibling practice world (portal search and creation
 * happen in the practice world, never in the host save).
 *
 * <p>The source world is captured at method head; destination computation
 * runs synchronously on the server thread, so the field never races.
 */
@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin121 {
    @Unique
    private ServerWorld practice121Source;

    @Inject(method = "createTeleportTarget", at = @At("HEAD"))
    private void captureSourceWorld(ServerWorld world, Entity entity, BlockPos pos,
            CallbackInfoReturnable<?> info) {
        practice121Source = world;
    }

    @Redirect(method = "createTeleportTarget",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"))
    private RegistryKey<World> reportVanillaKey(ServerWorld world) {
        if (world instanceof PracticeWorld121) {
            return ((PracticeWorld121) world).vanillaKey();
        }
        return world.getRegistryKey();
    }

    @Redirect(method = "createTeleportTarget",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getWorld(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/server/world/ServerWorld;"))
    private ServerWorld resolvePracticeDestination(MinecraftServer server, RegistryKey<World> key) {
        ServerWorld source = practice121Source;
        practice121Source = null;
        if (source instanceof PracticeWorld121) {
            ServerWorld sibling = ((PracticeWorld121) source).associatedLevel(key);
            if (sibling != null) {
                return sibling;
            }
        }
        return server.getWorld(key);
    }
}
