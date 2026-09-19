package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.adapter121.client.ClientScreens121;
import com.gregor0410.speedrunpractice.common.adapter.GuiAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeResult;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.util.OptionalLong;

/**
 * GUI entry points on the 1.21.1 client. Calls from any thread are
 * marshalled to the client thread; on a dedicated server every call fails
 * readably and {@link #isAvailable()} is false. Only
 * {@link ClientScreens121} touches client classes, and only behind the
 * environment check, so this class stays load-safe on servers.
 */
final class LiveGui121 implements GuiAdapter {
    @Override
    public void openMainMenu(PracticePlayer player) throws PracticeException {
        requirePlayer(player, "openMainMenu");
        openClient(new OpenMainMenu());
    }

    @Override
    public void openScenarioScreen(PracticePlayer player, PracticePreset preset) throws PracticeException {
        requirePlayer(player, "openScenarioScreen");
        if (preset == null) {
            throw new IllegalArgumentException("preset must not be null");
        }
        String customId = preset.type() == PracticeType.CUSTOM ? preset.id().value() : null;
        openClient(new OpenScenario(preset, customId));
    }

    @Override
    public void openResultsScreen(PracticePlayer player, PracticeResult result) throws PracticeException {
        requirePlayer(player, "openResultsScreen");
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        openClient(new OpenResults(result));
    }

    @Override
    public boolean isAvailable() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    private void openClient(Runnable opener) throws PracticeException {
        if (!isAvailable()) {
            throw new PracticeException("GUI needs the game client",
                    "The practice menu needs the game client. Use /practice commands instead.");
        }
        ClientScreens121.openOnClientThread(opener);
    }

    private static void requirePlayer(PracticePlayer player, String operation) throws PracticeException {
        if (!(player instanceof LivePlayer121)) {
            throw new PracticeException(operation + " got a foreign player handle",
                    "That player belongs to another session. Stop and start the practice again.");
        }
    }

    private static final class OpenMainMenu implements Runnable {
        @Override
        public void run() {
            ClientScreens121.openMain();
        }
    }

    private static final class OpenScenario implements Runnable {
        private final PracticePreset preset;
        private final String customId;

        private OpenScenario(PracticePreset preset, String customId) {
            this.preset = preset;
            this.customId = customId;
        }

        @Override
        public void run() {
            ClientScreens121.openSetup(preset, customId);
        }
    }

    private static final class OpenResults implements Runnable {
        private final PracticeResult result;

        private OpenResults(PracticeResult result) {
            this.result = result;
        }

        @Override
        public void run() {
            PracticeMenuModel.ResultsScreen model;
            if (Runtime121.runtime() == null) {
                model = new PracticeMenuModel.ResultsScreen(
                        result.practice().value(), result.elapsedMs(), null, 0);
            } else {
                OptionalLong pb = Runtime121.runtime().statistics().personalBest(result.practice());
                model = new PracticeMenuModel.ResultsScreen(result.practice().value(), result.elapsedMs(),
                        pb.isPresent() ? pb.getAsLong() : null,
                        Runtime121.runtime().statistics().attempts(result.practice()));
            }
            ClientScreens121.openResults(model);
        }
    }
}
