package com.gregor0410.speedrunpractice.adapter116.client;

import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import com.gregor0410.speedrunpractice.practices.ScenarioRegistry;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** Statistics screen: attempts, completions and PB per practice. */
final class PracticeStatsScreen extends PracticeScreenBase {
    /** Snapshot backing the screen (statistics reads are synchronized). */
    static final class Data {
        final PracticeMenuModel.StatisticsScreen model;

        Data(PracticeMenuModel.StatisticsScreen model) {
            this.model = model;
        }

        static Data snapshot(PracticeRuntime runtime) {
            List<PracticeMenuModel.StatisticsScreen.Row> rows =
                    new ArrayList<PracticeMenuModel.StatisticsScreen.Row>();
            for (PracticeType type : ScenarioRegistry.types()) {
                if (type == PracticeType.CUSTOM) {
                    continue;
                }
                PracticeId id = PracticeId.of(type.id());
                OptionalLong pb = runtime.statistics().personalBest(id);
                rows.add(new PracticeMenuModel.StatisticsScreen.Row(type.displayName(),
                        runtime.statistics().attempts(id), runtime.statistics().completed(id),
                        pb.isPresent() ? pb.getAsLong() : null));
            }
            return new Data(new PracticeMenuModel.StatisticsScreen(rows));
        }
    }

    private final Data data;

    PracticeStatsScreen(Data data) {
        super("Practice Statistics");
        this.data = data;
    }

    @Override
    protected void init() {
        smallButton(width / 2 - 155, height - 28, 150, "Reset all", button ->
                ClientScreens.onServer((server, entity, runtime) -> {
                    runtime.statistics().resetAll();
                    entity.sendMessage(new net.minecraft.text.LiteralText("Statistics reset."), false);
                    net.minecraft.client.MinecraftClient.getInstance().execute(ClientScreens::openStats);
                }));
        smallButton(width / 2 + 5, height - 28, 150, "Back", button -> ClientScreens.openMain());
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        int y = 34;
        List<PracticeMenuModel.StatisticsScreen.Row> rows = data.model.rows();
        int shown = Math.min(9, rows.size());
        for (int i = 0; i < shown; i++) {
            PracticeMenuModel.StatisticsScreen.Row row = rows.get(i);
            String pb = row.personalBestMs() == null
                    ? "--"
                    : PracticeMenuModel.ResultsScreen.formatTime(row.personalBestMs());
            line(matrices, row.displayName() + ": " + row.attempts() + " att / "
                    + row.completed() + " done / PB " + pb, y, 0xFFFFFF);
            y += 12;
        }
        if (rows.size() > shown) {
            line(matrices, "+" + (rows.size() - shown) + " more", y, 0x808080);
        }
    }
}
