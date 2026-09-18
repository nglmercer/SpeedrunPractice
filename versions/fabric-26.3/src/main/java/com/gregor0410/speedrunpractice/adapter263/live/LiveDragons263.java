package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.adapter263.world.PracticeLevel263;
import com.gregor0410.speedrunpractice.common.adapter.DragonAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;

import java.util.List;

/**
 * Dragon-fight control on the live end level. Reset mirrors the legacy end
 * start (fresh fight state plus the obsidian platform): living dragons are
 * discarded, a default fight is installed and initialized exactly like a
 * newly created end level, the saved state is replaced, and the spike
 * crystals are restored. The fight then spawns a fresh dragon on its own
 * tick once the arena is loaded.
 */
final class LiveDragons263 implements DragonAdapter {
    @Override
    public void resetFight(PracticeWorld world) throws PracticeException {
        LiveWorld263 handle = requireHandle(world, "resetFight");
        ServerLevel backing = handle.level();
        for (EnderDragon dragon : backing.getDragons()) {
            dragon.discard();
        }
        EnderDragonFight fresh = EnderDragonFight.createDefault();
        fresh.init(backing, backing.getSeed(), BlockPos.ZERO);
        backing.setDragonFight(fresh);
        backing.getDataStorage().set(EnderDragonFight.TYPE, fresh);
        fresh.resetSpikeCrystals();
        PracticeLevel263.buildObsidianPlatform(backing);
    }

    @Override
    public void forcePerch(PracticeWorld world) throws PracticeException {
        LiveWorld263 handle = requireHandle(world, "forcePerch");
        EnderDragon dragon = livingDragon(handle.level());
        if (dragon == null) {
            throw new PracticeException("forcePerch found no living dragon",
                    "No living dragon to perch. Start the fight first.");
        }
        dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING_APPROACH);
    }

    @Override
    public boolean hasLivingDragon(PracticeWorld world) throws PracticeException {
        LiveWorld263 handle = requireHandle(world, "hasLivingDragon");
        return livingDragon(handle.level()) != null;
    }

    private static EnderDragon livingDragon(ServerLevel level) {
        List<? extends EnderDragon> dragons = level.getDragons();
        for (EnderDragon dragon : dragons) {
            if (dragon.isAlive()) {
                return dragon;
            }
        }
        return null;
    }

    private static LiveWorld263 requireHandle(PracticeWorld world, String operation) throws PracticeException {
        if (!(world instanceof LiveWorld263)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        return (LiveWorld263) world;
    }
}
