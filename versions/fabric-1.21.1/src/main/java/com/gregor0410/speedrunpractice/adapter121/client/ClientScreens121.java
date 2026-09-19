package com.gregor0410.speedrunpractice.adapter121.client;

import com.gregor0410.speedrunpractice.adapter121.live.LivePlayer121;
import com.gregor0410.speedrunpractice.adapter121.live.Runtime121;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/**
 * Client-side screen openers plus the client/server bridge. All runtime
 * mutations run on the server thread via {@link #onServer}; screens only
 * read snapshots or issue those tasks. Nothing in this class may run on a
 * dedicated server (the client entrypoint and {@code LiveGui121}'s
 * environment check keep it client-only), but the class itself must stay
 * load-safe there: no client types in static initializers.
 */
public final class ClientScreens121 {
    private ClientScreens121() {
    }

    /** Work that needs the server thread and the in-world player. */
    public interface ServerTask {
        void run(MinecraftServer server, ServerPlayerEntity entity, PracticeRuntime runtime)
                throws PracticeException;
    }

    /**
     * Runs {@code task} on the server thread with the client's player,
     * reporting failures as chat messages. Safe to call from screens and
     * keybinds; a no-op with a message when no world is open.
     */
    public static void onServer(ServerTask task) {
        MinecraftClient client = MinecraftClient.getInstance();
        PracticeRuntime runtime = Runtime121.runtime();
        if (runtime == null) {
            tell("The practice runtime is not armed.");
            return;
        }
        MinecraftServer server = client.getServer();
        if (server == null) {
            tell("Open a single-player world first.");
            return;
        }
        UUID uuid = client.player == null ? null : client.player.getUuid();
        server.execute(() -> {
            ServerPlayerEntity entity = null;
            if (uuid != null) {
                entity = server.getPlayerManager().getPlayer(uuid);
            }
            if (entity == null) {
                List<ServerPlayerEntity> all = server.getPlayerManager().getPlayerList();
                entity = all.isEmpty() ? null : all.get(0);
            }
            if (entity == null) {
                entityTell(server, "Nobody is in the world.");
                return;
            }
            try {
                task.run(server, entity, runtime);
            } catch (PracticeException failure) {
                entity.sendMessage(Text.literal(failure.getUserMessage()), false);
            } catch (RuntimeException failure) {
                SpeedrunLogger.warn("Practice action failed: " + failure);
                entity.sendMessage(
                        Text.literal("Something went wrong. Check the log and try again."), false);
            }
        });
    }

    /** Wraps a server player for runtime calls. */
    public static LivePlayer121 handle(ServerPlayerEntity entity) {
        return new LivePlayer121(entity);
    }

    /** Chat message on the client thread (for pre-server failures). */
    public static void tell(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal(message), false);
            }
        });
    }

    private static void entityTell(MinecraftServer server, String message) {
        List<ServerPlayerEntity> all = server.getPlayerManager().getPlayerList();
        if (!all.isEmpty()) {
            all.get(0).sendMessage(Text.literal(message), false);
        }
    }

    public static void open(Screen screen) {
        MinecraftClient.getInstance().setScreen(screen);
    }

    /** Runs screen work on the client thread from any thread. */
    public static void openOnClientThread(Runnable opener) {
        MinecraftClient.getInstance().execute(opener);
    }

    public static void openMain() {
        open(new PracticeMainScreen121());
    }

    public static void openSetup(PracticePreset preset, String customId) {
        onServer((server, entity, runtime) -> {
            PracticeMenuModel.ScenarioScreen model = PracticeMenuModel.ScenarioScreen.forPreset(
                    preset, runtime.adapter(), ClientData121.loadoutIds(runtime));
            MinecraftClient.getInstance().execute(() -> open(new PracticeSetupScreen121(model, customId)));
        });
    }

    public static void openResults(PracticeMenuModel.ResultsScreen model) {
        open(new PracticeResultsScreen121(model));
    }

    public static void openSeeds() {
        onServer((server, entity, runtime) -> {
            PracticeSeedScreen121.Data data = PracticeSeedScreen121.Data.snapshot(runtime);
            MinecraftClient.getInstance().execute(() -> open(new PracticeSeedScreen121(data)));
        });
    }

    public static void openLoadouts() {
        onServer((server, entity, runtime) -> {
            PracticeLoadoutScreen121.Data data = PracticeLoadoutScreen121.Data.snapshot(runtime, null);
            MinecraftClient.getInstance().execute(() -> open(new PracticeLoadoutScreen121(data)));
        });
    }

    public static void openStats() {
        PracticeRuntime runtime = Runtime121.runtime();
        if (runtime == null) {
            tell("The practice runtime is not armed.");
            return;
        }
        // Statistics reads are synchronized; snapshots may build on any thread.
        open(new PracticeStatsScreen121(PracticeStatsScreen121.Data.snapshot(runtime)));
    }
}
