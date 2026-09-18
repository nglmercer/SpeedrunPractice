package com.gregor0410.speedrunpractice.adapter263;

import com.gregor0410.speedrunpractice.adapter263.live.Runtime263;
import net.fabricmc.api.ModInitializer;

/**
 * 26.3 entrypoint: arms the shared practice runtime on the live 26.3
 * adapter. Adapter methods gain real 26.3 implementations incrementally
 * (see versions/README.md); anything still pending fails with a readable
 * domain error instead of crashing.
 */
public final class SpeedrunPractice263 implements ModInitializer {
    @Override
    public void onInitialize() {
        Runtime263.initialize();
    }
}
