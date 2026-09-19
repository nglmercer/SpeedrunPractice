package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.adapter.CommandAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.commands.PracticeCommands;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Builds real Brigadier nodes from the shared command tree on 1.21.1. There
 * is no legacy tree here, so every shared node registers, including the
 * full {@code seed} subtree and the short practice aliases.
 */
final class LiveCommands121 implements CommandAdapter {
    @Override
    public void register(final PracticeCommands.Node root, final CommandExecutor executor) {
        if (root == null || executor == null) {
            throw new IllegalArgumentException("command root and executor must not be null");
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> builder = literal(root.name());
            for (PracticeCommands.Node child : root.children().values()) {
                builder.then(build(child, executor));
            }
            dispatcher.register(builder);
            SpeedrunLogger.info("Registered /" + root.name() + " (" + root.children().size()
                    + " shared children) on the 1.21.1 dispatcher");
        });
    }

    private static ArgumentBuilder<ServerCommandSource, ?> build(PracticeCommands.Node node,
            CommandExecutor executor) {
        ArgumentBuilder<ServerCommandSource, ?> builder;
        if (node.name().startsWith("<") && !node.args().isEmpty()) {
            PracticeCommands.Arg arg = node.args().get(0);
            RequiredArgumentBuilder<ServerCommandSource, ?> argument =
                    argument(arg.name(), brigadierType(arg.type()));
            if (node.action() != null) {
                argument.executes(context -> run(context, node.action(), executor));
            }
            builder = argument;
        } else {
            LiteralArgumentBuilder<ServerCommandSource> literal = literal(node.name());
            if (node.action() != null) {
                literal.executes(context -> run(context, node.action(), executor));
            }
            for (PracticeCommands.Arg arg : node.args()) {
                RequiredArgumentBuilder<ServerCommandSource, ?> argument =
                        argument(arg.name(), brigadierType(arg.type()));
                if (node.action() != null) {
                    argument.executes(context -> run(context, node.action(), executor));
                }
                literal.then(argument);
            }
            builder = literal;
        }
        for (PracticeCommands.Node child : node.children().values()) {
            builder.then(build(child, executor));
        }
        return builder;
    }

    private static com.mojang.brigadier.arguments.ArgumentType<?> brigadierType(PracticeCommands.ArgType type) {
        if (type == null) {
            return StringArgumentType.word();
        }
        switch (type) {
            case LONG:
                return LongArgumentType.longArg();
            case INT:
                return integer();
            case STRING:
                return StringArgumentType.greedyString();
            case WORD:
            default:
                return StringArgumentType.word();
        }
    }

    private static int run(CommandContext<ServerCommandSource> context, String action,
            CommandExecutor executor) {
        try {
            return executor.execute(new View(context, action));
        } catch (PracticeException failure) {
            context.getSource().sendError(Text.literal(failure.getUserMessage()));
            return 0;
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Practice command \"" + action + "\" failed: " + failure);
            context.getSource().sendError(
                    Text.literal("Something went wrong. Check the log and try again."));
            return 0;
        }
    }

    /** Shared-code view of one Brigadier invocation. */
    private static final class View implements CommandContextView {
        private final CommandContext<ServerCommandSource> context;
        private final String action;

        private View(CommandContext<ServerCommandSource> context, String action) {
            this.context = context;
            this.action = action;
        }

        @Override
        public String action() {
            return action;
        }

        @Override
        public PracticePlayer player() throws PracticeException {
            ServerPlayerEntity player;
            try {
                player = context.getSource().getPlayerOrThrow();
            } catch (CommandSyntaxException missing) {
                throw new PracticeException("Practice command \"" + action + "\" ran without a player",
                        "That command needs a player. Run it in-game.");
            }
            return new LivePlayer121(player);
        }

        @Override
        public boolean hasArg(String name) {
            try {
                context.getArgument(name, Object.class);
                return true;
            } catch (IllegalArgumentException missing) {
                return false;
            }
        }

        @Override
        public String stringArg(String name) {
            return context.getArgument(name, String.class);
        }

        @Override
        public long longArg(String name) {
            Long value = context.getArgument(name, Long.class);
            return value == null ? 0L : value;
        }

        @Override
        public int intArg(String name) {
            Integer value = context.getArgument(name, Integer.class);
            return value == null ? 0 : value;
        }

        @Override
        public void feedback(String message) {
            context.getSource().sendFeedback(() -> Text.literal(message), false);
        }
    }
}
