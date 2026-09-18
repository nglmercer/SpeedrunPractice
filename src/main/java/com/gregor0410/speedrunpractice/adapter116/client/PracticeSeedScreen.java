package com.gregor0410.speedrunpractice.adapter116.client;

import com.gregor0410.speedrunpractice.common.seeds.SeedResult;
import com.gregor0410.speedrunpractice.common.seeds.SeedSearchTask;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Seed-search screen: saved presets, live counters for the running search,
 * and matched seeds. Preset execution arrives with plan step 10; until
 * then Start explains honestly and Cancel/refresh work for real.
 */
final class PracticeSeedScreen extends PracticeScreenBase {
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

    PracticeSeedScreen(Data data) {
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
                ClientScreens.tell("Seed search presets are not runnable in this build yet."));
        smallButton(width / 2 - 155, 174, 150, "Cancel search", button ->
                ClientScreens.onServer((server, entity, runtime) -> {
                    boolean cancelled = runtime.cancelSearch();
                    entity.sendMessage(new net.minecraft.text.LiteralText(
                            cancelled ? "Seed search cancelled." : "No seed search is running."), false);
                    refreshOnClient(data.presetIdx);
                }));
        smallButton(width / 2 + 5, 174, 150, "Back", button -> ClientScreens.openMain());
    }

    private void refresh(int presetIdx) {
        ClientScreens.onServer((server, entity, runtime) ->
                refreshOnClient(presetIdx, runtime));
    }

    private void refreshOnClient(int presetIdx) {
        ClientScreens.onServer((server, entity, runtime) ->
                refreshOnClient(presetIdx, runtime));
    }

    private static void refreshOnClient(int presetIdx, PracticeRuntime runtime) {
        Data fresh = Data.snapshot(runtime, presetIdx);
        net.minecraft.client.MinecraftClient.getInstance()
                .execute(() -> ClientScreens.open(new PracticeSeedScreen(fresh)));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        int y = 34;
        if (data.presets.isEmpty()) {
            line(matrices, "No presets yet. Save searches to run them here.", y, 0xA0A0A0);
            y += 12;
        }
        if (data.progress == null) {
            line(matrices, "No seed search has been started.", y, 0xA0A0A0);
        } else {
            line(matrices, "Tested: " + data.progress.seedsTested()
                    + "  Matched: " + data.progress.seedsMatched()
                    + "  Verified: " + data.progress.seedsVerified(), y, 0xFFFFFF);
            y += 12;
            String state = data.progress.finished() ? "finished"
                    : data.progress.cancelled() ? "cancelled" : "running";
            line(matrices, "State: " + state + "  Failed: " + data.progress.seedsFailed(), y, 0xA0A0A0);
            y += 12;
            for (int i = 0; i < data.seeds.size(); i++) {
                line(matrices, "Seed: " + data.seeds.get(i), y, 0x55FF55);
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
