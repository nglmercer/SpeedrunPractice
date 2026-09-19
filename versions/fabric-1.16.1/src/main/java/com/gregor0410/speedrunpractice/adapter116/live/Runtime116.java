package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.adapter116.AdapterSet116;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.commands.PracticeCommands;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import com.gregor0410.speedrunpractice.practices.SpeedrunPracticeBootstrap;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

import java.nio.file.Path;

/**
 * Version-local wiring for the new runtime on 1.16.1 (plan step 02):
 *
 * <pre>
 * SpeedrunPractice initializer
 * → PracticeRuntime
 * → ScenarioEngine
 * → AdapterSet116 → LiveAdapter116
 * → real Minecraft 1.16.1
 * </pre>
 *
 * <p>Holds the one runtime and live adapter for this game instance, feeds the
 * engine from the server tick (completion polls there until event wiring
 * lands in step 05), and shuts the runtime down with the server. The legacy
 * command tree keeps running untouched beside the new one.
 */
public final class Runtime116 {
    private static LiveAdapter116 live;
    private static PracticeRuntime runtime;
    private static EventPoller116 poller;
    private static boolean running;

    private Runtime116() {
    }

    /** Starts the new runtime once; safe to call only from the initializer. */
    public static synchronized void initialize() {
        if (running) {
            return;
        }
        running = true;
        live = new LiveAdapter116();
        AdapterSet116 adapter = new AdapterSet116(live);
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("speedrun-practice-new");
        runtime = SpeedrunPracticeBootstrap.create(adapter, configDir);
        poller = new EventPoller116(live);
        try {
            adapter.commands().register(PracticeCommands.buildTree(), runtime.asCommandExecutor());
        } catch (PracticeException failure) {
            SpeedrunLogger.warn("New-runtime commands did not register: " + failure.getUserMessage());
        }
        ServerLifecycleEvents.SERVER_STARTED.register(server -> live.setServer(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            live.setServer(null);
            runtime.shutdown();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        TempLavaProbe116.arm(); // TEMP probe: revert after passing
        SpeedrunLogger.info("New practice runtime armed for 1.16.1 (config: " + configDir + ")");
    }

    /** The armed runtime, or null before {@link #initialize()}. */
    public static PracticeRuntime runtime() {
        return runtime;
    }

    /** The live adapter, or null before {@link #initialize()}. */
    public static LiveAdapter116 live() {
        return live;
    }

    private static void tick() {
        if (runtime == null || !runtime.hasActive()) {
            return;
        }
        try {
            PracticeState before = currentState();
            // Live events first (timer triggers, event completion), then the
            // scenario poll; either path may finish the attempt.
            poller.poll(runtime);
            runtime.tick();
            PracticeState after = currentState();
            if (before == PracticeState.RUNNING && after == PracticeState.COMPLETED) {
                announceCompletion();
            }
        } catch (PracticeException failure) {
            SpeedrunLogger.warn("Practice tick failed: " + failure.getUserMessage());
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Practice tick failed: " + failure);
        }
    }

    private static PracticeState currentState() {
        if (runtime.engine().currentContext() == null
                || runtime.engine().currentContext().session() == null) {
            return null;
        }
        return runtime.engine().currentContext().session().state();
    }

    private static void announceCompletion() {
        try {
            long ms = runtime.timer().elapsedMs();
            String name = runtime.engine().currentScenario().id().value();
            if (runtime.engine().currentContext() != null
                    && runtime.engine().currentContext().player() instanceof LivePlayer) {
                ServerPlayerEntity player =
                        ((LivePlayer) runtime.engine().currentContext().player()).entity();
                player.sendMessage(new LiteralText("§aCompleted " + name + " in " + formatTime(ms) + "!"),
                        false);
                try {
                    live.gui().openResultsScreen(runtime.engine().currentContext().player(),
                            new com.gregor0410.speedrunpractice.common.api.PracticeResult(
                                    runtime.engine().currentScenario().id(),
                                    runtime.engine().currentContext().seed(), ms,
                                    com.gregor0410.speedrunpractice.common.api.PracticeResult.Status.COMPLETED));
                } catch (com.gregor0410.speedrunpractice.common.api.PracticeException unavailable) {
                    // Dedicated server: chat message above is the whole UI.
                }
            }
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                SpeedrunLogger.info("Completed " + name + " in " + formatTime(ms));
            }
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Could not announce completion: " + failure.getMessage());
        }
    }

    private static String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = ms % 1000;
        StringBuilder out = new StringBuilder();
        if (minutes > 0) {
            out.append(minutes).append(':');
            if (seconds < 10) {
                out.append('0');
            }
        }
        out.append(seconds).append('.');
        if (millis < 100) {
            out.append('0');
        }
        if (millis < 10) {
            out.append('0');
        }
        return out.append(millis).toString();
    }
}
