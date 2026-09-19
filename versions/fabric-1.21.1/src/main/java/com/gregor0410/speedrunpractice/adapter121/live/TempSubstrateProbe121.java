package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * TEMPORARY micro probe: analyzes one seed ({@code 70}) to capture the
 * verifier's substrate dump for the phantom-hole hunt. No worlds, no
 * ground truth. Delete after passing.
 */
public final class TempSubstrateProbe121 {
    private static final long[] SEEDS = {70L};
    private static boolean armed;

    private TempSubstrateProbe121() {
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
            }, "probe-substrate-121");
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void probe(MinecraftServer server) {
        LiveAdapter121 live = Runtime121.live();
        for (int i = 0; i < 600 && (live == null || live.server() == null); i++) {
            sleep(500);
            live = Runtime121.live();
        }
        if (live == null || live.server() == null) {
            SpeedrunLogger.warn("PROBESUB121 ABORT no-live-adapter");
            return;
        }
        for (long seed : SEEDS) {
            try {
                SeedAnalyzer.SeedAnalysis analysis = live.seeds().analyze(seed, SeedQuery.builder()
                        .version(GameVersion.MC_1_21_1).requireLava().build());
                SpeedrunLogger.warn("PROBESUB121 SEED=" + seed + " STAGEB=" + analysis.matches());
            } catch (RuntimeException failure) {
                SpeedrunLogger.warn("PROBESUB121 SEED=" + seed + " ANALYZE-FAIL " + failure);
            }
        }
        SpeedrunLogger.warn("PROBESUB121 DONE");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
