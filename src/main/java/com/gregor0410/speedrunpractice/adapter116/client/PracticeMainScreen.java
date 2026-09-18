package com.gregor0410.speedrunpractice.adapter116.client;

import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;

/** Main menu: one tile per practice plus loadouts/stats/seed-search. */
final class PracticeMainScreen extends PracticeScreenBase {
    PracticeMainScreen() {
        super("Speedrun Practice");
    }

    @Override
    protected void init() {
        List<PracticeMenuModel.MenuEntry> entries = PracticeMenuModel.defaultMenu();
        int leftX = width / 2 - 155;
        int rightX = width / 2 + 5;
        for (int i = 0; i < entries.size(); i++) {
            final PracticeMenuModel.MenuEntry entry = entries.get(i);
            int x = (i % 2 == 0) ? leftX : rightX;
            int y = 40 + (i / 2) * 24;
            smallButton(x, y, 150, entry.displayName(), button -> openSetup(entry));
        }
        int baseY = 40 + ((entries.size() + 1) / 2) * 24 + 8;
        smallButton(leftX, baseY, 150, "Loadouts", button -> ClientScreens.openLoadouts());
        smallButton(rightX, baseY, 150, "Statistics", button -> ClientScreens.openStats());
        smallButton(leftX, baseY + 24, 150, "Seed Search", button -> ClientScreens.openSeeds());
        smallButton(rightX, baseY + 24, 150, "Close", button -> ClientScreens.open(null));
    }

    private void openSetup(PracticeMenuModel.MenuEntry entry) {
        ClientScreens.onServer((server, entity, runtime) -> {
            if (entry.type() == PracticeType.CUSTOM) {
                List<String> customs = ClientData.customIds(runtime);
                if (customs.isEmpty()) {
                    entity.sendMessage(new net.minecraft.text.LiteralText(
                            "No custom scenarios. Add JSON files to config/speedrun-practice-new/scenarios/."), false);
                    return;
                }
                String customId = customs.get(0);
                PracticePreset preset = new PracticePreset(PracticeId.of(customId), customId,
                        PracticeType.CUSTOM, runtime.customScenarios().get(customId).toSettings());
                net.minecraft.client.MinecraftClient.getInstance()
                        .execute(() -> ClientScreens.openSetup(preset, customId));
                return;
            }
            PracticePreset preset = new PracticePreset(PracticeId.of(entry.type().id()),
                    entry.displayName(), entry.type(), new PracticeSettings());
            net.minecraft.client.MinecraftClient.getInstance()
                    .execute(() -> ClientScreens.openSetup(preset, null));
        });
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
    }
}
