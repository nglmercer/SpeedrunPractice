package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.adapter.DragonAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Dragon-fight control on the live end world. Reset mirrors the legacy end
 * start (fresh fight data plus the obsidian platform); the perch uses the
 * phase manager directly instead of the legacy {@code /data} command.
 */
final class LiveDragons implements DragonAdapter {
    @Override
    public void resetFight(PracticeWorld world) throws PracticeException {
        LiveWorld handle = requireHandle(world, "resetFight");
        ServerWorld backing = handle.world();
        backing.getServer().getSaveProperties().method_29037(new CompoundTag());
        ServerWorld.createEndSpawnPlatform(backing);
    }

    @Override
    public void forcePerch(PracticeWorld world) throws PracticeException {
        LiveWorld handle = requireHandle(world, "forcePerch");
        EnderDragonEntity dragon = livingDragon(handle.world());
        if (dragon == null) {
            throw new PracticeException("forcePerch found no living dragon",
                    "No living dragon to perch. Start the fight first.");
        }
        dragon.getPhaseManager().setPhase(PhaseType.LANDING_APPROACH);
    }

    @Override
    public boolean hasLivingDragon(PracticeWorld world) throws PracticeException {
        LiveWorld handle = requireHandle(world, "hasLivingDragon");
        return livingDragon(handle.world()) != null;
    }

    private static EnderDragonEntity livingDragon(ServerWorld world) {
        Box everywhere = new Box(-30000000.0, -30000000.0, -30000000.0,
                30000000.0, 30000000.0, 30000000.0);
        List<EnderDragonEntity> dragons =
                world.getEntities(EnderDragonEntity.class, everywhere, EnderDragonEntity::isAlive);
        return dragons.isEmpty() ? null : dragons.get(0);
    }

    private static LiveWorld requireHandle(PracticeWorld world, String operation) throws PracticeException {
        if (!(world instanceof LiveWorld)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        return (LiveWorld) world;
    }
}
