package com.gregor0410.speedrunpractice.adapter263;

import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.fabricmc.api.ModInitializer;

/**
 * 26.3 entrypoint. Proves the shared engine links into the live game; adapter
 * methods gain real 26.3 implementations incrementally (see versions/README.md).
 */
public final class SpeedrunPractice263 implements ModInitializer {
    @Override
    public void onInitialize() {
        AdapterSet263 adapters = new AdapterSet263();
        SpeedrunLogger.info("SpeedrunPractice enabled for " + adapters.version().versionString());
    }
}
