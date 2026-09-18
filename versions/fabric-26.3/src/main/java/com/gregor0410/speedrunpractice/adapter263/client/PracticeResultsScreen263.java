package com.gregor0410.speedrunpractice.adapter263.client;

import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Post-attempt screen: time, PB and retry actions. */
final class PracticeResultsScreen263 extends PracticeScreenBase263 {
    private final PracticeMenuModel.ResultsScreen model;

    PracticeResultsScreen263(PracticeMenuModel.ResultsScreen model) {
        super(model.displayName() + " complete");
        this.model = model;
    }

    @Override
    protected void init() {
        centeredButton(96, "Retry same seed", button -> reset(PracticeScenario.ResetMode.SAME_SEED, true));
        centeredButton(120, "New seed", button -> reset(PracticeScenario.ResetMode.NEW_SEED, true));
        centeredButton(144, "Previous seed", button -> reset(PracticeScenario.ResetMode.PREVIOUS_SEED, true));
        centeredButton(168, "Save seed to favorites", button ->
                ClientScreens263.onServer((server, entity, runtime) -> {
                    long seed = runtime.favoriteCurrentSeed();
                    entity.sendSystemMessage(Component.literal("Favorited seed " + seed + "."));
                }));
        centeredButton(192, "Main menu", button -> ClientScreens263.openMain());
    }

    private void reset(PracticeScenario.ResetMode mode, boolean close) {
        ClientScreens263.onServer((server, entity, runtime) -> {
            runtime.reset(mode);
            long seed = runtime.currentSeed().isPresent() ? runtime.currentSeed().getAsLong() : -1L;
            entity.sendSystemMessage(Component.literal(
                    seed == -1L ? "Restarted." : "Restarted on seed " + seed + "."));
            if (close) {
                net.minecraft.client.Minecraft.getInstance().execute(() -> ClientScreens263.open(null));
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        line(graphics, "Time: " + PracticeMenuModel.ResultsScreen.formatTime(model.timeMs()), 36, 0x55FF55);
        String pb = model.personalBestMs() == null
                ? "--"
                : PracticeMenuModel.ResultsScreen.formatTime(model.personalBestMs());
        line(graphics, "Personal best: " + pb, 50, 0xFFFFFF);
        line(graphics, "Attempts: " + model.attempts(), 64, 0xA0A0A0);
    }
}
