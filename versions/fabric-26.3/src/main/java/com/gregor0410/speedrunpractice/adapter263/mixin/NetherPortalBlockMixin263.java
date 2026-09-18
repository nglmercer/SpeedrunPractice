package com.gregor0410.speedrunpractice.adapter263.mixin;

import com.gregor0410.speedrunpractice.adapter263.world.PracticeLevel263;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps nether-portal travel inside the practice triple (mirrors the 1.16.1
 * entity mixin). Practice levels use {@code speedrun_practice} keys, so two
 * redirects make vanilla logic triple-aware: the source level reports its
 * vanilla key (correct destination choice), and the destination lookup
 * resolves the sibling practice level (portal search and creation happen in
 * the practice world, never in the host save).
 *
 * <p>The source level is captured at method head; portal destination runs
 * synchronously on the server thread, so the field never races.
 */
@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin263 {
    @Unique
    private ServerLevel practice263Source;

    @Inject(method = "getPortalDestination", at = @At("HEAD"))
    private void captureSourceLevel(ServerLevel level, Entity entity, BlockPos pos,
            CallbackInfoReturnable<TeleportTransition> info) {
        practice263Source = level;
    }

    @Redirect(method = "getPortalDestination",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"))
    private ResourceKey<Level> reportVanillaKey(ServerLevel level) {
        if (level instanceof PracticeLevel263) {
            return ((PracticeLevel263) level).vanillaKey();
        }
        return level.dimension();
    }

    @Redirect(method = "getPortalDestination",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel resolvePracticeDestination(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel source = practice263Source;
        practice263Source = null;
        if (source instanceof PracticeLevel263) {
            ServerLevel sibling = ((PracticeLevel263) source).associatedLevel(key);
            if (sibling != null) {
                return sibling;
            }
        }
        return server.getLevel(key);
    }
}
