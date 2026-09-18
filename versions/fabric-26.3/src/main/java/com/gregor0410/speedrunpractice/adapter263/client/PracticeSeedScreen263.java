package com.gregor0410.speedrunpractice.adapter263.client;

import com.gregor0410.speedrunpractice.common.seeds.SeedResult;
import com.gregor0410.speedrunpractice.common.seeds.SeedSearchTask;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seed-search screen: saved presets, live counters for the running search,
 * and matched seeds. Start runs the selected preset through the same
 * runtime path as {@code /practice seeds search}; Cancel and refresh work
 * for real.
 */
final class PracticeSeedScreen263 extends PracticeScreenBase263 {
    /** Server-thread snapshot backing the screen. */
    static final class Data {
        final List<String> presets;
        final int presetIdx;
        final SeedSearchTask.SearchProgress progress;
        final List<Long> seeds;

        Data(List<String> presets, int presetIdx, SeedSearchTask.SearchProgress progress, List<Long> seeds) {
            this.presets = presets;
            this.presetIdx = presetIdx;
            this.progress = progress;
            this.seeds = seeds;
        }

        static Data snapshot(PracticeRuntime runtime) {
            return snapshot(runtime, 0);
        }

        static Data snapshot(PracticeRuntime runtime, int presetIdx) {
            List<String> presets = new ArrayList<String>();
            try {
                presets.addAll(runtime.seedStore().listSearches());
            } catch (java.io.IOException bad) {
                // Empty list; the screen explains where presets live.
            }
            int idx = presets.isEmpty() ? 0 : Math.max(0, Math.min(presetIdx, presets.size() - 1));
            SeedSearchTask.SearchProgress progress = runtime.searchProgress();
            List<Long> seeds = new ArrayList<Long>();
            SeedSearchTask task = runtime.activeSearch();
            if (task != null) {
                List<SeedResult> results = task.results();
                for (int i = 0; i < results.size() && i < 8; i++) {
                    seeds.add(results.get(i).seed());
                }
            }
            return new Data(presets, idx, progress, seeds);
        }
    }

    private final Data data;
    private int ticks;

    PracticeSeedScreen263(Data data) {
        super("Seed Search");
        this.data = data;
    }

    @Override
    protected void init() {
        String preset = data.presets.isEmpty() ? "(no presets)" : data.presets.get(data.presetIdx);
        smallButton(width / 2 - 155, 150, 150, "Preset: " + preset, button -> {
            if (!data.presets.isEmpty()) {
                refresh((data.presetIdx + 1) % data.presets.size());
            }
        });
        smallButton(width / 2 + 5, 150, 150, "Start search", button ->
                ClientScreens263.onServer((server, entity, runtime) -> {
                    if (data.presets.isEmpty()) {
                        entity.sendSystemMessage(Component.literal(
                                "No presets yet. Save searches to run them here."));
                        return;
                    }
                    String started = runtime.startPresetSearch(data.presets.get(data.presetIdx));
                    entity.sendSystemMessage(Component.literal(started));
                    refreshOnClient(data.presetIdx, runtime);
                }));
        smallButton(width / 2 - 155, 174, 150, "Cancel search", button ->
                ClientScreens263.onServer((server, entity, runtime) -> {
                    boolean cancelled = runtime.cancelSearch();
                    entity.sendSystemMessage(Component.literal(
                            cancelled ? "Seed search cancelled." : "No seed search is running."));
                    refreshOnClient(data.presetIdx, runtime);
                }));
        smallButton(width / 2 + 5, 174, 150, "Back", button -> ClientScreens263.openMain());
    }

    private void refresh(int presetIdx) {
        ClientScreens263.onServer((server, entity, runtime) ->
                refreshOnClient(presetIdx, runtime));
    }

    private static void refreshOnClient(int presetIdx, PracticeRuntime runtime) {
        Data fresh = Data.snapshot(runtime, presetIdx);
        net.minecraft.client.Minecraft.getInstance()
                .execute(() -> ClientScreens263.open(new PracticeSeedScreen263(fresh)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int y = 34;
        if (data.presets.isEmpty()) {
            line(graphics, "No presets yet. Save searches to run them here.", y, 0xA0A0A0);
            y += 12;
        }
        if (data.progress == null) {
            line(graphics, "No seed search has been started.", y, 0xA0A0A0);
        } else {
            line(graphics, "Tested: " + data.progress.seedsTested()
                    + "  Matched: " + data.progress.seedsMatched()
                    + "  Verified: " + data.progress.seedsVerified(), y, 0xFFFFFF);
            y += 12;
            String state = data.progress.finished() ? "finished"
                    : data.progress.cancelled() ? "cancelled" : "running";
            line(graphics, "State: " + state + "  Failed: " + data.progress.seedsFailed(), y, 0xA0A0A0);
            y += 12;
            for (int i = 0; i < data.seeds.size(); i++) {
                line(graphics, "Seed: " + data.seeds.get(i), y, 0x55FF55);
                y += 10;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Live counters while a search runs.
        if (data.progress != null && !data.progress.finished() && !data.progress.cancelled()
                && ++ticks % 40 == 0) {
            refresh(data.presetIdx);
        }
    }
}
