package com.gregor0410.speedrunpractice.adapter116.client;

import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import net.minecraft.client.util.math.MatrixStack;

/** Post-attempt screen: time, PB and retry actions. */
final class PracticeResultsScreen extends PracticeScreenBase {
    private final PracticeMenuModel.ResultsScreen model;

    PracticeResultsScreen(PracticeMenuModel.ResultsScreen model) {
        super(model.displayName() + " complete");
        this.model = model;
    }

    @Override
    protected void init() {
        centeredButton(96, "Retry same seed", button -> reset(PracticeScenario.ResetMode.SAME_SEED, true));
        centeredButton(120, "New seed", button -> reset(PracticeScenario.ResetMode.NEW_SEED, true));
        centeredButton(144, "Previous seed", button -> reset(PracticeScenario.ResetMode.PREVIOUS_SEED, true));
        centeredButton(168, "Save seed to favorites", button -> ClientScreens.onServer((server, entity, runtime) -> {
            long seed = runtime.favoriteCurrentSeed();
            entity.sendMessage(new net.minecraft.text.LiteralText("Favorited seed " + seed + "."), false);
        }));
        centeredButton(192, "Main menu", button -> ClientScreens.openMain());
    }

    private void reset(PracticeScenario.ResetMode mode, boolean close) {
        ClientScreens.onServer((server, entity, runtime) -> {
            runtime.reset(mode);
            long seed = runtime.currentSeed().isPresent() ? runtime.currentSeed().getAsLong() : -1L;
            entity.sendMessage(new net.minecraft.text.LiteralText(
                    seed == -1L ? "Restarted." : "Restarted on seed " + seed + "."), false);
            if (close) {
                net.minecraft.client.MinecraftClient.getInstance().execute(() -> ClientScreens.open(null));
            }
        });
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        line(matrices, "Time: " + PracticeMenuModel.ResultsScreen.formatTime(model.timeMs()), 36, 0x55FF55);
        String pb = model.personalBestMs() == null
                ? "--"
                : PracticeMenuModel.ResultsScreen.formatTime(model.personalBestMs());
        line(matrices, "Personal best: " + pb, 50, 0xFFFFFF);
        line(matrices, "Attempts: " + model.attempts(), 64, 0xA0A0A0);
    }
}
