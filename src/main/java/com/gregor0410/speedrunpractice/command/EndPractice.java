package com.gregor0410.speedrunpractice.command;

import net.minecraft.text.Text;
import com.gregor0410.speedrunpractice.SpeedrunPractice;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

public class EndPractice implements Command<ServerCommandSource> {
    @Override
    public int run(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        long seed;
        try {
            seed = ctx.getArgument("seed", long.class);
        } catch (IllegalArgumentException e) {
            seed = SpeedrunPractice.random.nextLong();
        }
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        player.setSpawnPoint(World.OVERWORLD, null, 0.0f, false, false);
        Practice.resetPlayer(player);
        Practice.getInventory(player, "end");
        Practice.startSpeedrunIGTTimer();
        return 1;
    }
}
