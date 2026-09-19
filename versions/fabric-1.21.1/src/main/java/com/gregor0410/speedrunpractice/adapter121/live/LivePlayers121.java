package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.adapter121.world.PracticeWorld121;
import com.gregor0410.speedrunpractice.common.adapter.PlayerAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Player operations on the live server player. Teleports resolve the target
 * world from the tracked practice set (single active practice, single
 * player), clamp Y upward out of solid blocks, and mirror the legacy spawn
 * bookkeeping so deaths respawn at the practice overworld.
 */
final class LivePlayers121 implements PlayerAdapter {
    private final LiveAdapter121 adapter;

    LivePlayers121(LiveAdapter121 adapter) {
        this.adapter = adapter;
    }

    @Override
    public void teleport(PracticePlayer player, PracticePosition position) throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "teleport");
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        ServerWorld target = teleportTarget(entity);
        BlockPos safe = clampUp(target, position);
        entity.teleport(target, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                position.yaw(), position.pitch());
        mirrorLegacySpawn(entity, target);
    }

    @Override
    public void setHealth(PracticePlayer player, double health) throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "setHealth");
        entity.setHealth((float) Math.max(0.0, Math.min(health, entity.getMaxHealth())));
    }

    @Override
    public void setFood(PracticePlayer player, int food) throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "setFood");
        entity.getHungerManager().setFoodLevel(Math.max(0, Math.min(food, 20)));
    }

    @Override
    public void clearEffects(PracticePlayer player) throws PracticeException {
        requireEntity(player, "clearEffects").clearStatusEffects();
    }

    @Override
    public void applyLoadout(PracticePlayer player, Loadout loadout) throws PracticeException {
        adapter.inventories().applyLoadout(player, loadout);
    }

    @Override
    public void resetPlayer(PracticePlayer player) throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "resetPlayer");
        entity.setHealth(entity.getMaxHealth());
        entity.experienceLevel = 0;
        entity.totalExperience = 0;
        entity.experienceProgress = 0.0f;
        entity.getHungerManager().setFoodLevel(20);
        entity.getHungerManager().setSaturationLevel(5.0f);
        entity.clearStatusEffects();
        entity.setVelocity(Vec3d.ZERO);
        entity.setAir(entity.getMaxAir());
        entity.setAbsorptionAmount(0.0f);
        entity.fallDistance = 0.0f;
        entity.seenCredits = false;
    }

    @Override
    public PracticePosition getPosition(PracticePlayer player) throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "getPosition");
        return new PracticePosition(entity.getX(), entity.getY(), entity.getZ(),
                entity.getYaw(), entity.getPitch());
    }

    @Override
    public double getHealth(PracticePlayer player) throws PracticeException {
        return requireEntity(player, "getHealth").getHealth();
    }

    @Override
    public int getFood(PracticePlayer player) throws PracticeException {
        return requireEntity(player, "getFood").getHungerManager().getFoodLevel();
    }

    @Override
    public PracticeWorld getWorld(PracticePlayer player) throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "getWorld");
        ServerWorld world = entity.getServerWorld();
        LiveWorld121 tracked = adapter.tracked(world.getRegistryKey());
        if (tracked != null) {
            return tracked;
        }
        return new LiveWorld121(world, world.getSeed(), dimensionOf(world));
    }

    @Override
    public PracticeCheckpoint.PlayerSnapshot capturePlayerState(PracticePlayer player)
            throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "capturePlayerState");
        Loadout inventory = null;
        try {
            inventory = adapter.inventories().captureLoadout(player, "checkpoint");
        } catch (PracticeException bad) {
            SpeedrunLogger.warn("Checkpoint inventory capture failed: " + bad.getUserMessage());
        }
        List<String> effects = new ArrayList<String>();
        for (StatusEffectInstance effect : entity.getStatusEffects()) {
            String id = effect.getEffectType().getKey()
                    .map(key -> key.getValue().toString()).orElse(null);
            if (id == null) {
                continue;
            }
            effects.add(id + "|" + effect.getDuration() + "|" + effect.getAmplifier()
                    + "|" + effect.isAmbient() + "|" + effect.shouldShowParticles()
                    + "|" + effect.shouldShowIcon());
        }
        return new PracticeCheckpoint.PlayerSnapshot(
                new PracticePosition(entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYaw(), entity.getPitch()),
                entity.getHealth(),
                entity.getHungerManager().getFoodLevel(),
                entity.getHungerManager().getSaturationLevel(),
                entity.experienceLevel,
                entity.totalExperience,
                inventory,
                effects,
                entity.getInventory().selectedSlot);
    }

    @Override
    public void restorePlayerState(PracticePlayer player, PracticeCheckpoint.PlayerSnapshot snapshot)
            throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "restorePlayerState");
        if (snapshot == null || snapshot.position() == null) {
            throw new IllegalArgumentException("snapshot and position must not be null");
        }
        teleport(player, snapshot.position());
        setHealth(player, snapshot.health());
        setFood(player, snapshot.food());
        entity.getHungerManager().setSaturationLevel(Math.max(0.0f, snapshot.saturation()));
        entity.experienceLevel = 0;
        entity.totalExperience = 0;
        entity.experienceProgress = 0.0f;
        entity.addExperience(Math.max(0, snapshot.xpPoints()));
        entity.clearStatusEffects();
        for (String raw : snapshot.effects()) {
            StatusEffectInstance effect = parseEffect(raw);
            if (effect != null) {
                entity.addStatusEffect(effect);
            }
        }
        if (snapshot.inventory() != null) {
            applyLoadout(player, snapshot.inventory());
        }
        entity.getInventory().selectedSlot =
                Math.max(0, Math.min(snapshot.selectedSlot(), 8));
        entity.playerScreenHandler.sendContentUpdates();
    }

    private static StatusEffectInstance parseEffect(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("\\|");
        if (parts.length != 6) {
            SpeedrunLogger.warn("Skipping bad checkpoint effect \"" + raw + "\"");
            return null;
        }
        RegistryEntry<StatusEffect> type;
        try {
            Identifier id = Identifier.of(parts[0].trim());
            Optional<RegistryEntry.Reference<StatusEffect>> entry =
                    Registries.STATUS_EFFECT.getEntry(id);
            if (entry == null || !entry.isPresent()) {
                SpeedrunLogger.warn("Skipping unknown checkpoint effect \"" + parts[0] + "\"");
                return null;
            }
            type = entry.get();
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Skipping bad checkpoint effect \"" + raw + "\"");
            return null;
        }
        try {
            int duration = Math.max(1, Integer.parseInt(parts[1].trim()));
            int amplifier = Math.max(0, Integer.parseInt(parts[2].trim()));
            boolean ambient = Boolean.parseBoolean(parts[3].trim());
            boolean particles = Boolean.parseBoolean(parts[4].trim());
            boolean icon = Boolean.parseBoolean(parts[5].trim());
            return new StatusEffectInstance(type, duration, amplifier, ambient, particles, icon);
        } catch (NumberFormatException bad) {
            SpeedrunLogger.warn("Skipping bad checkpoint effect \"" + raw + "\"");
            return null;
        }
    }

    private ServerWorld teleportTarget(ServerPlayerEntity entity) {
        MinecraftServer server = adapter.server();
        ServerWorld current = entity.getServerWorld();
        // The scenario's world wins (fresh starts teleport across worlds);
        // otherwise the player stays where they are.
        LiveWorld121 practice = adapter.currentWorld();
        if (practice != null && server != null
                && server.getWorld(practice.world().getRegistryKey()) != null) {
            return practice.world();
        }
        return current;
    }

    private static BlockPos clampUp(ServerWorld world, PracticePosition position) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(
                position.blockX(), position.blockY(), position.blockZ());
        int minY = world.getBottomY();
        int maxY = world.getTopY() - 2;
        if (mutable.getY() < minY) {
            mutable.setY(minY);
        }
        while (mutable.getY() < maxY
                && (!world.isAir(mutable) || !world.isAir(mutable.up()))) {
            mutable.setY(mutable.getY() + 1);
        }
        return mutable.toImmutable();
    }

    private void mirrorLegacySpawn(ServerPlayerEntity entity, ServerWorld target) {
        MinecraftServer server = adapter.server();
        if (server == null || adapter.tracked(target.getRegistryKey()) == null) {
            return;
        }
        Map<PracticeDimension, LiveWorld121> triple = adapter.triple(target.getRegistryKey());
        if (triple != null && triple.get(PracticeDimension.OVERWORLD) != null
                && triple.get(PracticeDimension.OVERWORLD).world() instanceof PracticeWorld121) {
            // Linked practices respawn at the practice overworld (legacy parity):
            // dimension set, no explicit pos, not forced.
            entity.setSpawnPoint(triple.get(PracticeDimension.OVERWORLD).world().getRegistryKey(),
                    null, 0.0f, false, false);
            return;
        }
        if (triple == null) {
            // Single end worlds respawn at the vanilla overworld (legacy parity).
            entity.setSpawnPoint(World.OVERWORLD, null, 0.0f, false, false);
        }
    }

    static PracticeDimension dimensionOf(ServerWorld world) {
        // Practice worlds use speedrun_practice keys; their mirrored vanilla
        // dimension is the only correct answer (blind travel, nether exits
        // and end entries all key off this).
        if (world instanceof PracticeWorld121) {
            return ((PracticeWorld121) world).practiceDimension();
        }
        RegistryKey<World> key = world.getRegistryKey();
        if (World.NETHER.equals(key)) {
            return PracticeDimension.NETHER;
        }
        if (World.END.equals(key)) {
            return PracticeDimension.END;
        }
        return PracticeDimension.OVERWORLD;
    }

    private static ServerPlayerEntity requireEntity(PracticePlayer player, String operation)
            throws PracticeException {
        if (!(player instanceof LivePlayer121)) {
            throw new PracticeException(operation + " got a foreign player handle",
                    "That player belongs to another session. Stop and start the practice again.");
        }
        return ((LivePlayer121) player).entity();
    }
}
