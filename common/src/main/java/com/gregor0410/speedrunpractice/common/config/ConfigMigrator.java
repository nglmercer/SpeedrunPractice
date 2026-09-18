package com.gregor0410.speedrunpractice.common.config;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code v1 -> v2 -> future} config migration (plan section 35). */
public final class ConfigMigrator {
    public static final int CURRENT_SCHEMA = 2;

    private ConfigMigrator() {
    }

    /**
     * Migrates a raw config map to the current schema. v1 (legacy ModConfig
     * shape without {@code schemaVersion}) gains the v2 keys; unknown future
     * schemas are rejected so the caller can back up instead of destroying
     * user data.
     */
    public static Map<String, Object> migrate(Map<String, Object> raw, String origin) throws PracticeException {
        if (raw == null) {
            throw new IllegalArgumentException("config map must not be null");
        }
        int schema = 1;
        Object marker = raw.get("schemaVersion");
        if (marker instanceof Number) {
            schema = ((Number) marker).intValue();
        }
        if (schema > CURRENT_SCHEMA) {
            throw new PracticeException("Config schema v" + schema + " from " + origin + " is newer than supported v"
                    + CURRENT_SCHEMA,
                    "Your settings were made by a newer Speedrun Practice (v" + schema + "). "
                            + "They were backed up and defaults were loaded.");
        }
        if (schema < 1) {
            schema = 1;
        }
        Map<String, Object> migrated = new LinkedHashMap<String, Object>(raw);
        if (schema == 1) {
            migrated.put("schemaVersion", 2);
            if (!migrated.containsKey("keybinds")) {
                Map<String, Object> keys = new LinkedHashMap<String, Object>();
                keys.put("restartSame", "F6");
                keys.put("restartNew", "F7");
                keys.put("previousSeed", "F8");
                keys.put("saveCheckpoint", "F9");
                keys.put("loadCheckpoint", "F10");
                keys.put("stopPractice", "F11");
                keys.put("openMenu", "F5");
                migrated.put("keybinds", keys);
            }
            if (!migrated.containsKey("timerStart")) {
                migrated.put("timerStart", "player_move");
            }
            if (!migrated.containsKey("timerStop")) {
                migrated.put("timerStop", "scenario_complete");
            }
            SpeedrunLogger.info("Migrated config " + origin + " from schema v1 to v2");
        }
        return migrated;
    }
}
