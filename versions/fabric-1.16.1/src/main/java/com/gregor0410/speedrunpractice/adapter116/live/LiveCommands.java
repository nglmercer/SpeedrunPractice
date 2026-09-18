package com.gregor0410.speedrunpractice.adapter116.live;

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
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Builds real Brigadier nodes from the shared command tree. Nodes named
 * {@code <...>} with arguments become argument nodes; everything else is a
 * literal. Two spellings stay legacy-owned until the section 11 migration:
 * {@code legacy.*} placeholders (the legacy tree already implements those
 * spellings) plus the bare {@code seed} node and its seed-argument child
 * (registering those would merge-override the working legacy seed
 * commands); the non-colliding {@code seed next/previous/favorite} nodes
 * register normally.
 */
final class LiveCommands implements CommandAdapter {
    @Override
    public void register(final PracticeCommands.Node root, final CommandExecutor executor) {
        if (root == null || executor == null) {
            throw new IllegalArgumentException("command root and executor must not be null");
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            LiteralArgumentBuilder<ServerCommandSource> builder = literal(root.name());
            for (PracticeCommands.Node child : root.children().values()) {
                ArgumentBuilder<ServerCommandSource, ?> node = build(child, executor);
                if (node != null) {
                    builder.then(node);
                }
            }
            dispatcher.register(builder);
            SpeedrunLogger.info("Registered /" + root.name() + " (" + root.children().size()
                    + " shared children) on the 1.16.1 dispatcher");
        });
    }

    private static ArgumentBuilder<ServerCommandSource, ?> build(PracticeCommands.Node node,
            CommandExecutor executor) {
        if (node.action() != null && node.action().startsWith("legacy.")) {
            return null;
        }
        if ("seed".equals(node.name()) && "seed.show".equals(node.action())) {
            return seedMerge(node, executor);
        }
        if ("<seed>".equals(node.name()) && "seed.set".equals(node.action())) {
            return null;
        }
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
            ArgumentBuilder<ServerCommandSource, ?> built = build(child, executor);
            if (built != null) {
                builder.then(built);
            }
        }
        return builder;
    }

    /**
     * Builds the {@code seed} node without its own executes so Brigadier
     * merges it into the legacy {@code seed} node: legacy keeps the bare and
     * seed-argument forms while the new children attach alongside.
     */
    private static ArgumentBuilder<ServerCommandSource, ?> seedMerge(PracticeCommands.Node node,
            CommandExecutor executor) {
        LiteralArgumentBuilder<ServerCommandSource> literal = literal(node.name());
        for (PracticeCommands.Node child : node.children().values()) {
            ArgumentBuilder<ServerCommandSource, ?> built = build(child, executor);
            if (built != null) {
                literal.then(built);
            }
        }
        return literal;
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
            context.getSource().sendFeedback(new LiteralText(failure.getUserMessage()), false);
            return 0;
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Practice command \"" + action + "\" failed: " + failure);
            context.getSource().sendFeedback(
                    new LiteralText("Something went wrong. Check the log and try again."), false);
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
                player = context.getSource().getPlayer();
            } catch (CommandSyntaxException missing) {
                throw new PracticeException("Practice command \"" + action + "\" ran without a player",
                        "That command needs a player. Run it in-game.");
            }
            return new LivePlayer(player);
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
            context.getSource().sendFeedback(new LiteralText(message), false);
        }
    }
}
