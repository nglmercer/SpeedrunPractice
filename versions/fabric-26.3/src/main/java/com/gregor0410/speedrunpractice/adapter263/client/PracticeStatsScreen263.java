package com.gregor0410.speedrunpractice.adapter263.client;

import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import com.gregor0410.speedrunpractice.practices.ScenarioRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** Statistics screen: attempts, completions and PB per practice. */
final class PracticeStatsScreen263 extends PracticeScreenBase263 {
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

    PracticeStatsScreen263(Data data) {
        super("Practice Statistics");
        this.data = data;
    }

    @Override
    protected void init() {
        smallButton(width / 2 - 155, height - 28, 150, "Reset all", button ->
                ClientScreens263.onServer((server, entity, runtime) -> {
                    runtime.statistics().resetAll();
                    entity.sendSystemMessage(Component.literal("Statistics reset."));
                    net.minecraft.client.Minecraft.getInstance().execute(ClientScreens263::openStats);
                }));
        smallButton(width / 2 + 5, height - 28, 150, "Back", button -> ClientScreens263.openMain());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int y = 34;
        List<PracticeMenuModel.StatisticsScreen.Row> rows = data.model.rows();
        int shown = Math.min(9, rows.size());
        for (int i = 0; i < shown; i++) {
            PracticeMenuModel.StatisticsScreen.Row row = rows.get(i);
            String pb = row.personalBestMs() == null
                    ? "--"
                    : PracticeMenuModel.ResultsScreen.formatTime(row.personalBestMs());
            line(graphics, row.displayName() + ": " + row.attempts() + " att / "
                    + row.completed() + " done / PB " + pb, y, 0xFFFFFF);
            y += 12;
        }
        if (rows.size() > shown) {
            line(graphics, "+" + (rows.size() - shown) + " more", y, 0x808080);
        }
    }
}
