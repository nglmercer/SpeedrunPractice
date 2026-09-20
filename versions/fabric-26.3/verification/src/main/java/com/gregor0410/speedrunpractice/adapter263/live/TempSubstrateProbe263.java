package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * TEMPORARY micro probe: analyzes one seed ({@code 39}) to capture the
 * verifier's substrate dump for the phantom-hole hunt. No worlds, no
 * ground truth. Delete after passing.
 */
public final class TempSubstrateProbe263 {
    private static final long[] SEEDS = {39L};
    private static boolean armed;

    private TempSubstrateProbe263() {
    }

    /** TEMP: registers the one-shot probe. Call sites must be reverted. */
    public static synchronized void arm() {
        if (armed) {
            return;
        }
        armed = true;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    probe(server);
                }
            }, "probe-substrate-263");
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void probe(MinecraftServer server) {
        LiveAdapter263 live = Runtime263.live();
        for (int i = 0; i < 600 && (live == null || live.server() == null); i++) {
            sleep(500);
            live = Runtime263.live();
        }
        if (live == null || live.server() == null) {
            SpeedrunLogger.warn("PROBESUB263 ABORT no-live-adapter");
            return;
        }
        for (long seed : SEEDS) {
            try {
                SeedAnalyzer.SeedAnalysis analysis = live.seeds().analyze(seed, SeedQuery.builder()
                        .version(GameVersion.V_26_3).requireLava().build());
                SpeedrunLogger.warn("PROBESUB263 SEED=" + seed + " STAGEB=" + analysis.matches());
            } catch (RuntimeException failure) {
                SpeedrunLogger.warn("PROBESUB263 SEED=" + seed + " ANALYZE-FAIL " + failure);
            }
        }
        SpeedrunLogger.warn("PROBESUB263 DONE");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
