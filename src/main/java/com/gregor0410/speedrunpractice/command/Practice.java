package com.gregor0410.speedrunpractice.command;

import com.google.common.collect.Lists;
import com.gregor0410.speedrunpractice.IMinecraftServer;
import com.gregor0410.speedrunpractice.SpeedrunPractice;
import com.gregor0410.speedrunpractice.mixin.ServerPlayerEntityAccess;
import com.gregor0410.speedrunpractice.world.PracticeWorld;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Practice {
    public static int setSlot(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx,"slot");
        String key = ctx.getNodes().get(2).getNode().getName();
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        player.sendMessage(Text.literal(String.format("§aSet %s slot to §2§lslot %d", key,slot)),false);
        SpeedrunPractice.config.practiceSlots.put(key,slot-1);
        try {
            SpeedrunPractice.config.save();
        } catch (IOException e) {
            return 0;
        }
        return 1;
    }

    public static int saveSlot(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String key = ctx.getNodes().get(2).getNode().getName();
        int slot = IntegerArgumentType.getInteger(ctx,"slot");
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        player.sendMessage(Text.literal(String.format("§aSaved current inventory to §2§l%s slot %d",key, slot)),false);
        PlayerInventory inventory = player.getInventory();
        NbtList nbtList = new NbtList();
        inventory.writeNbt(nbtList);
        SpeedrunPractice.config.practiceInventories.putIfAbsent(key,new ArrayList<>());
        SpeedrunPractice.config.practiceInventories.get(key).set(slot-1,nbtList.stream().map(NbtElement::toString).collect(Collectors.toList()));
        try {
            SpeedrunPractice.config.save();
        } catch (IOException e) {
            return 0;
        }
        return 1;
    }

    public static void startSpeedrunIGTTimer(){
        if(SpeedrunPractice.speedrunIGTInterface!=null){
            SpeedrunPractice.speedrunIGTInterface.resetTimer();
        }
    }


    public static void getInventory(ServerPlayerEntity player, String key) {
        player.getInventory().clear();
        player.playerScreenHandler.sendContentUpdates();
        List<String> inventoryStringList = SpeedrunPractice.config.practiceInventories.getOrDefault(key, Lists.newArrayList(new ArrayList<>(),new ArrayList<>(),new ArrayList<>())).get(SpeedrunPractice.config.practiceSlots.getOrDefault(key,0));
        if(inventoryStringList!=null) {
            List<NbtCompound> inventoryTagList = inventoryStringList.stream().map(tag -> {
                try {
                    return StringNbtReader.parse(tag);
                } catch (CommandSyntaxException e) {
                    e.printStackTrace();
                }
                return null;
            }).collect(Collectors.toList());
            NbtList nbtList = new NbtList();
            nbtList.addAll(inventoryTagList);
            player.getInventory().readNbt(nbtList);
            player.playerScreenHandler.sendContentUpdates();
        }
    }

    public static void populatePostBlindInventory(ServerPlayerEntity player,long seed) {
    }

    static void resetPlayer(ServerPlayerEntity player) {
        player.setHealth(20f);
        player.setExperienceLevel(0);
        player.setExperiencePoints(0);
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(5f);
        player.clearStatusEffects();
        player.setVelocity(Vec3d.ZERO);
        player.setAir(300);
        ((ServerPlayerEntityAccess)player).setSeenCredits(false);
        SpeedrunPractice.autoSaveStater.deleteAllStates();
    }

    public static int seed(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String seed= String.valueOf(SpeedrunPractice.random.getSeed());
        Text text = Text.literal("[" + seed + "]");
        ctx.getSource().getPlayer().sendMessage(text,false);
        return 1;
    }

    public static int setSeed(CommandContext<ServerCommandSource> ctx) {
        SpeedrunPractice.random.seed.set(ctx.getArgument("seed",long.class));
        return 1;
    }

    public static int reloadSeedList(CommandContext<ServerCommandSource> ctx) {
        SpeedrunPractice.seedManager.reload();
        ctx.getSource().sendFeedback(() -> Text.literal("§aReloaded seed list. Found " + SpeedrunPractice.seedManager.getSeedCount() + " seeds."), false);
        return 1;
    }

    public static int toggleSeedList(CommandContext<ServerCommandSource> ctx) {
        SpeedrunPractice.config.useSeedList = !SpeedrunPractice.config.useSeedList;
        try {
            SpeedrunPractice.config.save();
        } catch (IOException e) {
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("§aSeed list is now " + (SpeedrunPractice.config.useSeedList ? "§2enabled" : "§4disabled")), false);
        return 1;
    }

    public static void linkedPracticeWorldPractice(CommandContext<ServerCommandSource> ctx, long seed, boolean spawnChunks, boolean netherSpawn,boolean createPortals, Function<ServerWorld,BlockPos> overworldPosProvider, String key) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),"/advancement revoke @a everything");
        server.execute(()-> {
            Practice.resetPlayer(player);
            Practice.getInventory(player, key);
            Practice.startSpeedrunIGTTimer();
        });
    }

    public static void setSpawnPos(ServerWorld overworld, ServerPlayerEntity player) {
        ServerPlayerEntityAccess playerAccess = (ServerPlayerEntityAccess) player;
        playerAccess.setSpawnPointDimension(overworld.getRegistryKey());
        playerAccess.setSpawnPointPosition(null);
    }
}
