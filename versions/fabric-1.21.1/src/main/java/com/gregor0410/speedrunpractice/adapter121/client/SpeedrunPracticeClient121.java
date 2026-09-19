package com.gregor0410.speedrunpractice.adapter121.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Client entrypoint: keybinds plus their tick polling. */
public final class SpeedrunPracticeClient121 implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PracticeKeys121.register();
        ClientTickEvents.END_CLIENT_TICK.register(PracticeKeys121::tick);
    }
}
