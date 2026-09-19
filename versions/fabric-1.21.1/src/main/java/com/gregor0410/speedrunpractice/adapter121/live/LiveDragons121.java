package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.adapter121.mixin.EnderDragonFightAccess121;
import com.gregor0410.speedrunpractice.adapter121.world.PracticeWorld121;
import com.gregor0410.speedrunpractice.common.adapter.DragonAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.level.LevelProperties;

import java.util.List;

/**
 * Dragon-fight control on the live end world. Reset mirrors the legacy end
 * start: living dragons are discarded, the old fight bar is cleared, a
 * fresh default fight is installed (saved state replaced too), and the
 * obsidian platform is rebuilt. The fight then spawns a fresh dragon on
 * its own tick once a player reaches the arena.
 */
final class LiveDragons121 implements DragonAdapter {
    private final LiveAdapter121 adapter;

    LiveDragons121(LiveAdapter121 adapter) {
        this.adapter = adapter;
    }

    @Override
    public void resetFight(PracticeWorld world) throws PracticeException {
        requireHandle(world, "resetFight");
        ServerWorld backing = LiveAdapter121.requireWorld(adapter, world, "resetFight");
        for (EnderDragonEntity dragon : backing.getAliveEnderDragons()) {
            dragon.discard();
        }
        EnderDragonFight old = backing.getEnderDragonFight();
        if (old != null) {
            try {
                ((EnderDragonFightAccess121) (Object) old).getBossBar().clearPlayers();
            } catch (RuntimeException missing) {
                // Bar cleanup is best-effort; the reset still lands.
            }
        }
        EnderDragonFight fresh =
                new EnderDragonFight(backing, backing.getSeed(), EnderDragonFight.Data.DEFAULT);
        backing.setEnderDragonFight(fresh);
        if (backing.getLevelProperties() instanceof LevelProperties) {
            ((LevelProperties) backing.getLevelProperties())
                    .setDragonFight(EnderDragonFight.Data.DEFAULT);
        }
        PracticeWorld121.buildObsidianPlatform(backing);
    }

    @Override
    public void forcePerch(PracticeWorld world) throws PracticeException {
        requireHandle(world, "forcePerch");
        LiveAdapter121.requireWorld(adapter, world, "forcePerch");
        EnderDragonEntity dragon = livingDragon(
                ((LiveWorld121) world).world());
        if (dragon == null) {
            throw new PracticeException("forcePerch found no living dragon",
                    "No living dragon to perch. Start the fight first.");
        }
        dragon.getPhaseManager().setPhase(PhaseType.LANDING_APPROACH);
    }

    @Override
    public boolean hasLivingDragon(PracticeWorld world) throws PracticeException {
        requireHandle(world, "hasLivingDragon");
        LiveAdapter121.requireWorld(adapter, world, "hasLivingDragon");
        return livingDragon(((LiveWorld121) world).world()) != null;
    }

    private static EnderDragonEntity livingDragon(ServerWorld world) {
        List<? extends EnderDragonEntity> dragons = world.getAliveEnderDragons();
        for (EnderDragonEntity dragon : dragons) {
            if (dragon != null && dragon.isAlive()) {
                return dragon;
            }
        }
        return null;
    }

    private static LiveWorld121 requireHandle(PracticeWorld world, String operation) throws PracticeException {
        if (!(world instanceof LiveWorld121)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        return (LiveWorld121) world;
    }
}
