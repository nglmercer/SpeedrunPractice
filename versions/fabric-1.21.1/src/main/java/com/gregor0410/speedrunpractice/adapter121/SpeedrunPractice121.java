package com.gregor0410.speedrunpractice.adapter121;

import com.gregor0410.speedrunpractice.adapter121.live.Runtime121;
import net.fabricmc.api.ModInitializer;

/** Mod entrypoint for the 1.21.1 build (plan section 9). */
public final class SpeedrunPractice121 implements ModInitializer {
    @Override
    public void onInitialize() {
        Runtime121.initialize();
    }
}
