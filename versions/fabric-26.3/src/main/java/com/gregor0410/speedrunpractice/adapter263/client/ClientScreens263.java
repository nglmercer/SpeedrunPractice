package com.gregor0410.speedrunpractice.adapter263.client;

import com.gregor0410.speedrunpractice.adapter263.live.LivePlayer263;
import com.gregor0410.speedrunpractice.adapter263.live.Runtime263;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Client-side screen openers plus the client/server bridge. All runtime
 * mutations run on the server thread via {@link #onServer}; screens only
 * read snapshots or issue those tasks. Nothing in this class may run on a
 * dedicated server (the client entrypoint and {@code LiveGui263}'s
 * environment check keep it client-only), but the class itself must stay
 * load-safe there: no client types in static initializers.
 */
public final class ClientScreens263 {
    private ClientScreens263() {
    }

    /** Work that needs the server thread and the in-world player. */
    public interface ServerTask {
        void run(MinecraftServer server, ServerPlayer entity, PracticeRuntime runtime)
                throws PracticeException;
    }

    /**
     * Runs {@code task} on the server thread with the client's player,
     * reporting failures as chat messages. Safe to call from screens and
     * keybinds; a no-op with a message when no world is open.
     */
    public static void onServer(ServerTask task) {
        Minecraft client = Minecraft.getInstance();
        PracticeRuntime runtime = Runtime263.runtime();
        if (runtime == null) {
            tell("The practice runtime is not armed.");
            return;
        }
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            tell("Open a single-player world first.");
            return;
        }
        UUID uuid = client.player == null ? null : client.player.getUUID();
        server.execute(() -> {
            ServerPlayer entity = null;
            if (uuid != null) {
                entity = server.getPlayerList().getPlayer(uuid);
            }
            if (entity == null) {
                List<ServerPlayer> all = server.getPlayerList().getPlayers();
                entity = all.isEmpty() ? null : all.get(0);
            }
            if (entity == null) {
                entityTell(server, "Nobody is in the world.");
                return;
            }
            try {
                task.run(server, entity, runtime);
            } catch (PracticeException failure) {
                entity.sendSystemMessage(Component.literal(failure.getUserMessage()));
            } catch (RuntimeException failure) {
                SpeedrunLogger.warn("Practice action failed: " + failure);
                entity.sendSystemMessage(
                        Component.literal("Something went wrong. Check the log and try again."));
            }
        });
    }

    /** Wraps a server player for runtime calls. */
    public static LivePlayer263 handle(ServerPlayer entity) {
        return new LivePlayer263(entity);
    }

    /** Chat message on the client thread (for pre-server failures). */
    public static void tell(String message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal(message));
            }
        });
    }

    private static void entityTell(MinecraftServer server, String message) {
        List<ServerPlayer> all = server.getPlayerList().getPlayers();
        if (!all.isEmpty()) {
            all.get(0).sendSystemMessage(Component.literal(message));
        }
    }

    public static void open(Screen screen) {
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    /** Runs screen work on the client thread from any thread. */
    public static void openOnClientThread(Runnable opener) {
        Minecraft.getInstance().execute(opener);
    }

    public static void openMain() {
        open(new PracticeMainScreen263());
    }

    public static void openSetup(PracticePreset preset, String customId) {
        onServer((server, entity, runtime) -> {
            PracticeMenuModel.ScenarioScreen model = PracticeMenuModel.ScenarioScreen.forPreset(
                    preset, runtime.adapter(), ClientData263.loadoutIds(runtime));
            Minecraft.getInstance().execute(() -> open(new PracticeSetupScreen263(model, customId)));
        });
    }

    public static void openResults(PracticeMenuModel.ResultsScreen model) {
        open(new PracticeResultsScreen263(model));
    }

    public static void openSeeds() {
        onServer((server, entity, runtime) -> {
            PracticeSeedScreen263.Data data = PracticeSeedScreen263.Data.snapshot(runtime);
            Minecraft.getInstance().execute(() -> open(new PracticeSeedScreen263(data)));
        });
    }

    public static void openLoadouts() {
        onServer((server, entity, runtime) -> {
            PracticeLoadoutScreen263.Data data = PracticeLoadoutScreen263.Data.snapshot(runtime, null);
            Minecraft.getInstance().execute(() -> open(new PracticeLoadoutScreen263(data)));
        });
    }

    public static void openStats() {
        PracticeRuntime runtime = Runtime263.runtime();
        if (runtime == null) {
            tell("The practice runtime is not armed.");
            return;
        }
        // Statistics reads are synchronized; snapshots may build on any thread.
        open(new PracticeStatsScreen263(PracticeStatsScreen263.Data.snapshot(runtime)));
    }
}
