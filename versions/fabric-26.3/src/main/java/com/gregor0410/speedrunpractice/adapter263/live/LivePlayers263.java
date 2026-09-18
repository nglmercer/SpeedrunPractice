package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.adapter.PlayerAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Player operations on the live server player. Teleports resolve the target
 * level from the tracked practice world (single active practice, single
 * player), clamp Y upward out of solid blocks, and cross dimensions through
 * a {@link TeleportTransition}. No spawn bookkeeping: 26.3 practices run in
 * the live vanilla levels, so deaths already respawn at the vanilla spawn.
 */
final class LivePlayers263 implements PlayerAdapter {
    private final LiveAdapter263 adapter;

    LivePlayers263(LiveAdapter263 adapter) {
        this.adapter = adapter;
    }

    @Override
    public void teleport(PracticePlayer player, PracticePosition position) throws PracticeException {
        ServerPlayer entity = requireEntity(player, "teleport");
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        ServerLevel target = teleportTarget(entity);
        BlockPos safe = clampUp(target, position);
        entity.teleport(new TeleportTransition(target,
                new Vec3(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5),
                Vec3.ZERO, position.yaw(), position.pitch(), TeleportTransition.DO_NOTHING));
    }

    @Override
    public void setHealth(PracticePlayer player, double health) throws PracticeException {
        ServerPlayer entity = requireEntity(player, "setHealth");
        entity.setHealth((float) Math.max(0.0, Math.min(health, entity.getMaxHealth())));
    }

    @Override
    public void setFood(PracticePlayer player, int food) throws PracticeException {
        ServerPlayer entity = requireEntity(player, "setFood");
        entity.getFoodData().setFoodLevel(Math.max(0, Math.min(food, 20)));
    }

    @Override
    public void clearEffects(PracticePlayer player) throws PracticeException {
        requireEntity(player, "clearEffects").removeAllEffects();
    }

    @Override
    public void applyLoadout(PracticePlayer player, Loadout loadout) throws PracticeException {
        adapter.inventories().applyLoadout(player, loadout);
    }

    @Override
    public void resetPlayer(PracticePlayer player) throws PracticeException {
        ServerPlayer entity = requireEntity(player, "resetPlayer");
        entity.setHealth(entity.getMaxHealth());
        entity.setExperienceLevels(0);
        entity.setExperiencePoints(0);
        entity.getFoodData().setFoodLevel(20);
        entity.getFoodData().setSaturation(5.0f);
        entity.removeAllEffects();
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setAirSupply(entity.getMaxAirSupply());
        entity.setAbsorptionAmount(0.0f);
        entity.resetFallDistance();
        entity.seenCredits = false;
    }

    @Override
    public PracticePosition getPosition(PracticePlayer player) throws PracticeException {
        ServerPlayer entity = requireEntity(player, "getPosition");
        return new PracticePosition(entity.getX(), entity.getY(), entity.getZ(),
                entity.getYRot(), entity.getXRot());
    }

    @Override
    public double getHealth(PracticePlayer player) throws PracticeException {
        return requireEntity(player, "getHealth").getHealth();
    }

    @Override
    public int getFood(PracticePlayer player) throws PracticeException {
        return requireEntity(player, "getFood").getFoodData().getFoodLevel();
    }

    @Override
    public PracticeWorld getWorld(PracticePlayer player) throws PracticeException {
        ServerPlayer entity = requireEntity(player, "getWorld");
        ServerLevel level = entity.level();
        LiveWorld263 tracked = adapter.tracked(level.dimension());
        if (tracked != null) {
            return tracked;
        }
        return new LiveWorld263(level, level.getSeed(), dimensionOf(level));
    }

    @Override
    public PracticeCheckpoint.PlayerSnapshot capturePlayerState(PracticePlayer player)
            throws PracticeException {
        ServerPlayer entity = requireEntity(player, "capturePlayerState");
        Loadout inventory = null;
        try {
            inventory = adapter.inventories().captureLoadout(player, "checkpoint");
        } catch (PracticeException bad) {
            SpeedrunLogger.warn("Checkpoint inventory capture failed: " + bad.getUserMessage());
        }
        List<String> effects = new ArrayList<String>();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            String id = effect.getEffect().unwrapKey()
                    .map(key -> key.identifier().toString()).orElse(null);
            if (id == null) {
                continue;
            }
            effects.add(id + "|" + effect.getDuration() + "|" + effect.getAmplifier()
                    + "|" + effect.isAmbient() + "|" + effect.isVisible() + "|" + effect.showIcon());
        }
        return new PracticeCheckpoint.PlayerSnapshot(
                new PracticePosition(entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYRot(), entity.getXRot()),
                entity.getHealth(),
                entity.getFoodData().getFoodLevel(),
                entity.getFoodData().getSaturationLevel(),
                entity.experienceLevel,
                entity.totalExperience,
                inventory,
                effects,
                entity.getInventory().getSelectedSlot());
    }

    @Override
    public void restorePlayerState(PracticePlayer player, PracticeCheckpoint.PlayerSnapshot snapshot)
            throws PracticeException {
        ServerPlayer entity = requireEntity(player, "restorePlayerState");
        if (snapshot == null || snapshot.position() == null) {
            throw new IllegalArgumentException("snapshot and position must not be null");
        }
        teleport(player, snapshot.position());
        setHealth(player, snapshot.health());
        setFood(player, snapshot.food());
        entity.getFoodData().setSaturation(Math.max(0.0f, snapshot.saturation()));
        entity.setExperienceLevels(0);
        entity.setExperiencePoints(0);
        entity.giveExperiencePoints(Math.max(0, snapshot.xpPoints()));
        entity.removeAllEffects();
        for (String raw : snapshot.effects()) {
            MobEffectInstance effect = parseEffect(raw);
            if (effect != null) {
                entity.addEffect(effect);
            }
        }
        if (snapshot.inventory() != null) {
            applyLoadout(player, snapshot.inventory());
        }
        entity.getInventory().setSelectedSlot(Math.max(0, Math.min(snapshot.selectedSlot(), 8)));
        entity.inventoryMenu.broadcastChanges();
    }

    private static MobEffectInstance parseEffect(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("\\|");
        if (parts.length != 6) {
            SpeedrunLogger.warn("Skipping bad checkpoint effect \"" + raw + "\"");
            return null;
        }
        Holder<MobEffect> type;
        try {
            Identifier id = Identifier.tryParse(parts[0].trim());
            if (id == null || BuiltInRegistries.MOB_EFFECT.get(id).isEmpty()) {
                SpeedrunLogger.warn("Skipping unknown checkpoint effect \"" + parts[0] + "\"");
                return null;
            }
            type = BuiltInRegistries.MOB_EFFECT.get(id).get();
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Skipping bad checkpoint effect \"" + raw + "\"");
            return null;
        }
        try {
            int duration = Math.max(1, Integer.parseInt(parts[1].trim()));
            int amplifier = Math.max(0, Integer.parseInt(parts[2].trim()));
            boolean ambient = Boolean.parseBoolean(parts[3].trim());
            boolean visible = Boolean.parseBoolean(parts[4].trim());
            boolean icon = Boolean.parseBoolean(parts[5].trim());
            return new MobEffectInstance(type, duration, amplifier, ambient, visible, icon);
        } catch (NumberFormatException bad) {
            SpeedrunLogger.warn("Skipping bad checkpoint effect \"" + raw + "\"");
            return null;
        }
    }

    private ServerLevel teleportTarget(ServerPlayer entity) {
        MinecraftServer server = adapter.server();
        ServerLevel current = entity.level();
        // The scenario's world wins (fresh starts teleport across worlds);
        // otherwise the player stays where they are.
        LiveWorld263 practice = adapter.currentWorld();
        if (practice != null && server != null
                && server.getLevel(practice.level().dimension()) != null) {
            return practice.level();
        }
        return current;
    }

    private static BlockPos clampUp(ServerLevel level, PracticePosition position) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(
                position.blockX(), position.blockY(), position.blockZ());
        int minY = level.dimensionType().minY();
        int maxY = minY + level.dimensionType().height() - 2;
        if (mutable.getY() < minY) {
            mutable.setY(minY);
        }
        while (mutable.getY() < maxY
                && (!level.getBlockState(mutable).isAir()
                        || !level.getBlockState(mutable.above()).isAir())) {
            mutable.setY(mutable.getY() + 1);
        }
        return mutable.immutable();
    }

    static PracticeDimension dimensionOf(ServerLevel level) {
        ResourceKey<Level> key = level.dimension();
        if (Level.NETHER.equals(key)) {
            return PracticeDimension.NETHER;
        }
        if (Level.END.equals(key)) {
            return PracticeDimension.END;
        }
        return PracticeDimension.OVERWORLD;
    }

    private static ServerPlayer requireEntity(PracticePlayer player, String operation)
            throws PracticeException {
        if (!(player instanceof LivePlayer263)) {
            throw new PracticeException(operation + " got a foreign player handle",
                    "That player belongs to another session. Stop and start the practice again.");
        }
        return ((LivePlayer263) player).entity();
    }
}
