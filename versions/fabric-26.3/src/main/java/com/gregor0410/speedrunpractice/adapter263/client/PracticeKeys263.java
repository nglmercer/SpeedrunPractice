package com.gregor0410.speedrunpractice.adapter263.client;

import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The seven practice keybinds. Vanilla {@code KeyMapping}s self-register
 * on construction in this version (there is no Fabric keybinding module
 * for 26.3), so registration is plain construction; presses are polled on
 * the client tick, and every world-mutating action runs on the server
 * thread.
 */
public final class PracticeKeys263 {
    private PracticeKeys263() {
    }

    public static KeyMapping openMenu;
    public static KeyMapping restartSame;
    public static KeyMapping restartNew;
    public static KeyMapping previousSeed;
    public static KeyMapping saveCheckpoint;
    public static KeyMapping loadCheckpoint;
    public static KeyMapping stopPractice;

    public static void register() {
        openMenu = bind("open_menu", InputConstants.KEY_O);
        restartSame = bind("restart_same", InputConstants.KEY_R);
        restartNew = bind("restart_new", InputConstants.KEY_G);
        previousSeed = bind("previous_seed", InputConstants.KEY_H);
        saveCheckpoint = bind("save_checkpoint", InputConstants.KEY_C);
        loadCheckpoint = bind("load_checkpoint", InputConstants.KEY_V);
        stopPractice = bind("stop_practice", InputConstants.KEY_X);
    }

    private static KeyMapping bind(String name, int code) {
        return new KeyMapping("key.speedrun-practice." + name, InputConstants.Type.KEYBOARD, code,
                KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath("speedrun-practice", "practice")));
    }

    /** Polls presses; call on the client tick when a player exists. */
    public static void tick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        while (openMenu.consumeClick()) {
            ClientScreens263.openMain();
        }
        while (restartSame.consumeClick()) {
            reset(PracticeScenario.ResetMode.SAME_SEED);
        }
        while (restartNew.consumeClick()) {
            reset(PracticeScenario.ResetMode.NEW_SEED);
        }
        while (previousSeed.consumeClick()) {
            reset(PracticeScenario.ResetMode.PREVIOUS_SEED);
        }
        while (saveCheckpoint.consumeClick()) {
            ClientScreens263.onServer((server, entity, runtime) -> {
                runtime.saveCheckpoint();
                entity.sendSystemMessage(Component.literal("Checkpoint saved."));
            });
        }
        while (loadCheckpoint.consumeClick()) {
            ClientScreens263.onServer((server, entity, runtime) -> {
                runtime.restoreCheckpoint();
                entity.sendSystemMessage(Component.literal("Checkpoint restored."));
            });
        }
        while (stopPractice.consumeClick()) {
            ClientScreens263.onServer((server, entity, runtime) -> {
                runtime.stop();
                entity.sendSystemMessage(Component.literal("Practice stopped."));
            });
        }
    }

    private static void reset(PracticeScenario.ResetMode mode) {
        ClientScreens263.onServer((server, entity, runtime) -> {
            runtime.reset(mode);
            String seed = runtime.currentSeed().isPresent()
                    ? " on seed " + runtime.currentSeed().getAsLong()
                    : "";
            entity.sendSystemMessage(Component.literal("Restarted" + seed + "."));
        });
    }
}
