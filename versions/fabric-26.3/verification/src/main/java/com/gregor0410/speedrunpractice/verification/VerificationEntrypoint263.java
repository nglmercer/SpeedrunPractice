package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.adapter263.live.TempLavaProbe263;
import com.gregor0410.speedrunpractice.adapter263.live.AllProbe263;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Development-only verification command; this class is never in the release jar. */
public final class VerificationEntrypoint263 implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("practiceverify").then(literal("run").executes(context -> run(
                                context.getSource(), "all"))
                        .then(argument("suite", word()).executes(context -> run(
                                context.getSource(), getString(context, "suite")))))));
    }

    private static int run(CommandSourceStack source, String suite) {
        if (!knownSuite(suite)) {
            source.sendFailure(Component.literal("Unsupported verification suite: " + suite
                    + ". Available: worlds, structures, portals, dragon, registries, seed-search, "
                    + "scenarios, resets, checkpoints, lava, fixtures, all."));
            return 0;
        }
        try {
            if (!VerificationSession.start("26.3", suite)) {
                source.sendFailure(Component.literal("A verification run is already active."));
                return 0;
            }
            if (!"lava".equals(suite) && !"fixtures".equals(suite) && !"all".equals(suite)) {
                VerificationSession.fail("suite." + suite, "suite is registered but has no implementation yet");
                source.sendFailure(Component.literal("Verification suite " + suite + " is not implemented yet."));
                return 0;
            }
            if ("fixtures".equals(suite)) {
                AllProbe263.runFixturesNow(source.getServer());
            } else if ("all".equals(suite)) {
                AllProbe263.runNow(source.getServer());
            } else {
                TempLavaProbe263.runNow(source.getServer());
            }
        } catch (Exception failure) {
            source.sendFailure(Component.literal("Could not start verification: " + failure.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Verification suite " + suite + " started."), false);
        return 1;
    }

    private static boolean knownSuite(String suite) {
        return "worlds".equals(suite) || "structures".equals(suite) || "portals".equals(suite)
                || "dragon".equals(suite) || "registries".equals(suite) || "seed-search".equals(suite)
                || "scenarios".equals(suite) || "resets".equals(suite) || "checkpoints".equals(suite)
                || "lava".equals(suite) || "fixtures".equals(suite) || "all".equals(suite);
    }
}
