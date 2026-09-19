package com.gregor0410.speedrunpractice.adapter121.mixin;

import com.gregor0410.speedrunpractice.adapter121.world.PracticeWorld121;
import net.minecraft.block.EndPortalBlock;
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
 * Keeps end-portal travel inside the practice triple: worlds report their
 * vanilla key (correct end/overworld branch), and the destination lookup
 * resolves the sibling practice world. Practice end worlds point their
 * stored spawn at the practice overworld, so the end-to-overworld trip
 * needs no further help. (The credits detour in the collision handler keys
 * off the vanilla end key, so practice-end exits skip straight to the
 * portal — good practice UX.)
 *
 * <p>The source world is captured at method head; destination computation
 * runs synchronously on the server thread, so the field never races.
 */
@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin121 {
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
