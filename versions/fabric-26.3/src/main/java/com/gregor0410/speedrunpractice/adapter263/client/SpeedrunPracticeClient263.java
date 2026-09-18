package com.gregor0410.speedrunpractice.adapter263.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Client entrypoint: keybinds plus their tick polling. */
public final class SpeedrunPracticeClient263 implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PracticeKeys263.register();
        ClientTickEvents.END_CLIENT_TICK.register(PracticeKeys263::tick);
    }
}
