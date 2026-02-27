package com.gregor0410.speedrunpractice.command;

import com.gregor0410.speedrunpractice.SpeedrunPractice;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Revert implements Command<ServerCommandSource> {
    @Override
    public int run(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String split = ctx.getArgument("split",String.class);
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        try {
            if(!SpeedrunPractice.autoSaveStater.revertToSplit(split)) {
                player.sendMessage(Text.literal(String.format("No save state exists for split %s", split)), false);
                return -1;
            }
        } catch (Exception e) {
            player.sendMessage(Text.literal("An error occured - Delorean is probably not installed.").formatted(Formatting.RED),false);
            return -1;
        }
        return 1;
    }
}
