package com.gregor0410.speedrunpractice.adapter116.client;

import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

/**
 * The seven practice keybinds (plan step 08). Presses are polled on the
 * client tick; every world-mutating action runs on the server thread.
 */
public final class PracticeKeys {
    private PracticeKeys() {
    }

    public static KeyBinding openMenu;
    public static KeyBinding restartSame;
    public static KeyBinding restartNew;
    public static KeyBinding previousSeed;
    public static KeyBinding saveCheckpoint;
    public static KeyBinding loadCheckpoint;
    public static KeyBinding stopPractice;

    public static void register() {
        openMenu = bind("open_menu", GLFW.GLFW_KEY_O);
        restartSame = bind("restart_same", GLFW.GLFW_KEY_R);
        restartNew = bind("restart_new", GLFW.GLFW_KEY_G);
        previousSeed = bind("previous_seed", GLFW.GLFW_KEY_H);
        saveCheckpoint = bind("save_checkpoint", GLFW.GLFW_KEY_C);
        loadCheckpoint = bind("load_checkpoint", GLFW.GLFW_KEY_V);
        stopPractice = bind("stop_practice", GLFW.GLFW_KEY_X);
    }

    private static KeyBinding bind(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.speedrun-practice." + name, InputUtil.Type.KEYSYM, code, "category.speedrun-practice"));
    }

    /** Polls presses; call on the client tick when a player exists. */
    public static void tick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        while (openMenu.wasPressed()) {
            ClientScreens.openMain();
        }
        while (restartSame.wasPressed()) {
            reset(PracticeScenario.ResetMode.SAME_SEED);
        }
        while (restartNew.wasPressed()) {
            reset(PracticeScenario.ResetMode.NEW_SEED);
        }
        while (previousSeed.wasPressed()) {
            reset(PracticeScenario.ResetMode.PREVIOUS_SEED);
        }
        while (saveCheckpoint.wasPressed()) {
            ClientScreens.onServer((server, entity, runtime) -> {
                runtime.saveCheckpoint();
                entity.sendMessage(new LiteralText("Checkpoint saved."), false);
            });
        }
        while (loadCheckpoint.wasPressed()) {
            ClientScreens.onServer((server, entity, runtime) -> {
                runtime.restoreCheckpoint();
                entity.sendMessage(new LiteralText("Checkpoint restored."), false);
            });
        }
        while (stopPractice.wasPressed()) {
            ClientScreens.onServer((server, entity, runtime) -> {
                runtime.stop();
                entity.sendMessage(new LiteralText("Practice stopped."), false);
            });
        }
    }

    private static void reset(PracticeScenario.ResetMode mode) {
        ClientScreens.onServer((server, entity, runtime) -> {
            runtime.reset(mode);
            String seed = runtime.currentSeed().isPresent()
                    ? " on seed " + runtime.currentSeed().getAsLong()
                    : "";
            entity.sendMessage(new LiteralText("Restarted" + seed + "."), false);
        });
    }
}
