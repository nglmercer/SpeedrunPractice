package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.adapter116.live.TempLavaProbe116;
import com.gregor0410.speedrunpractice.adapter116.live.AllProbe116;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Development-only verification command; this class is never in the release jar. */
public final class VerificationEntrypoint116 implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> dispatcher.register(
                literal("practiceverify").then(literal("run").executes(context -> run(
                        context.getSource(), "all"))
                        .then(argument("suite", word()).executes(context -> run(
                                context.getSource(), getString(context, "suite")))))));
    }

    private static int run(ServerCommandSource source, String suite) {
        if (!knownSuite(suite)) {
            source.sendError(new LiteralText("Unsupported verification suite: " + suite
                    + ". Available: worlds, structures, portals, dragon, registries, seed-search, "
                    + "scenarios, resets, checkpoints, lava, fixtures, all."));
            return 0;
        }
        try {
            if (!VerificationSession.start("1.16.1", suite)) {
                source.sendError(new LiteralText("A verification run is already active."));
                return 0;
            }
            if ("fixtures".equals(suite)) {
                AllProbe116.runFixturesNow(source.getMinecraftServer());
            } else if ("all".equals(suite)) {
                AllProbe116.runNow(source.getMinecraftServer());
            } else if ("lava".equals(suite)) {
                TempLavaProbe116.runNow(source.getMinecraftServer());
            } else {
                AllProbe116.runSuiteNow(source.getMinecraftServer(), suite);
            }
        } catch (Exception failure) {
            source.sendError(new LiteralText("Could not start verification: " + failure.getMessage()));
            return 0;
        }
        source.sendFeedback(new LiteralText("Verification suite " + suite + " started."), false);
        return 1;
    }

    private static boolean knownSuite(String suite) {
        return "worlds".equals(suite) || "structures".equals(suite) || "portals".equals(suite)
                || "dragon".equals(suite) || "registries".equals(suite) || "seed-search".equals(suite)
                || "scenarios".equals(suite) || "resets".equals(suite) || "checkpoints".equals(suite)
                || "lava".equals(suite) || "fixtures".equals(suite) || "all".equals(suite);
    }
}
