package com.gregor0410.speedrunpractice.common.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configurable keybind actions (plan section 22). Defaults are suggestions;
 * nothing is hardcoded — users rebind through config and each version maps
 * these ids onto its own key system.
 */
public final class PracticeKeybinds {
    public enum Action {
        RESTART_SAME("restartSame", "F6"),
        RESTART_NEW("restartNew", "F7"),
        PREVIOUS_SEED("previousSeed", "F8"),
        SAVE_CHECKPOINT("saveCheckpoint", "F9"),
        LOAD_CHECKPOINT("loadCheckpoint", "F10"),
        STOP_PRACTICE("stopPractice", "F11"),
        OPEN_MENU("openMenu", "F5");

        private final String id;
        private final String defaultKey;

        Action(String id, String defaultKey) {
            this.id = id;
            this.defaultKey = defaultKey;
        }

        public String id() {
            return id;
        }

        public String defaultKey() {
            return defaultKey;
        }

        public static Action fromId(String id) {
            for (Action action : values()) {
                if (action.id.equals(id)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("Unknown keybind action: " + id);
        }
    }

    private final Map<String, String> bindings = new LinkedHashMap<String, String>();

    public PracticeKeybinds() {
        resetDefaults();
    }

    public void resetDefaults() {
        bindings.clear();
        for (Action action : Action.values()) {
            bindings.put(action.id(), action.defaultKey());
        }
    }

    public String get(Action action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        return bindings.get(action.id());
    }

    public void set(Action action, String key) {
        if (action == null || key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("action and key must be set");
        }
        bindings.put(action.id(), key.trim());
    }

    /** Loads user bindings; unknown actions ignored, missing ones keep defaults. */
    public void loadFrom(Map<String, String> stored) {
        if (stored == null) {
            return;
        }
        for (Map.Entry<String, String> entry : stored.entrySet()) {
            try {
                Action action = Action.fromId(entry.getKey());
                if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                    bindings.put(action.id(), entry.getValue().trim());
                }
            } catch (IllegalArgumentException unknown) {
                // Ignore bindings from newer versions.
            }
        }
    }

    public Map<String, String> toMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(bindings));
    }
}
