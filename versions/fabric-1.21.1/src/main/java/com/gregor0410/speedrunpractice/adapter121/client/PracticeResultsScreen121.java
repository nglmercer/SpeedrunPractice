package com.gregor0410.speedrunpractice.adapter121.client;

import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Post-attempt screen: time, PB and retry actions. */
final class PracticeResultsScreen121 extends PracticeScreenBase121 {
    private final PracticeMenuModel.ResultsScreen model;

    PracticeResultsScreen121(PracticeMenuModel.ResultsScreen model) {
        super(model.displayName() + " complete");
        this.model = model;
    }

    @Override
    protected void init() {
        centeredButton(96, "Retry same seed", button -> reset(PracticeScenario.ResetMode.SAME_SEED, true));
        centeredButton(120, "New seed", button -> reset(PracticeScenario.ResetMode.NEW_SEED, true));
        centeredButton(144, "Previous seed", button -> reset(PracticeScenario.ResetMode.PREVIOUS_SEED, true));
        centeredButton(168, "Save seed to favorites", button ->
                ClientScreens121.onServer((server, entity, runtime) -> {
                    long seed = runtime.favoriteCurrentSeed();
                    entity.sendMessage(Text.literal("Favorited seed " + seed + "."), false);
                }));
        centeredButton(192, "Main menu", button -> ClientScreens121.openMain());
    }

    private void reset(PracticeScenario.ResetMode mode, boolean close) {
        ClientScreens121.onServer((server, entity, runtime) -> {
            runtime.reset(mode);
            long seed = runtime.currentSeed().isPresent() ? runtime.currentSeed().getAsLong() : -1L;
            entity.sendMessage(Text.literal(
                    seed == -1L ? "Restarted." : "Restarted on seed " + seed + "."), false);
            if (close) {
                MinecraftClient.getInstance().execute(() -> ClientScreens121.open(null));
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        line(context, "Time: " + PracticeMenuModel.ResultsScreen.formatTime(model.timeMs()), 36, 0x55FF55);
        String pb = model.personalBestMs() == null
                ? "--"
                : PracticeMenuModel.ResultsScreen.formatTime(model.personalBestMs());
        line(context, "Personal best: " + pb, 50, 0xFFFFFF);
        line(context, "Attempts: " + model.attempts(), 64, 0xA0A0A0);
    }
}
