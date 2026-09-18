package com.gregor0410.speedrunpractice.common.config;

import com.gregor0410.speedrunpractice.common.util.SimpleJson;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned shared configuration (plan section 35). Legacy v1 files (the old
 * {@code ModConfig} shape without {@code schemaVersion}) migrate to v2; a
 * corrupt file is backed up, reported readably, and replaced by safe defaults.
 * Never silently deletes user configuration.
 */
public final class SpeedrunPracticeConfig {
    public static final String FILE_NAME = "speedrun-practice.json";

    public int schemaVersion = ConfigMigrator.CURRENT_SCHEMA;
    public int defaultMaxDist = 1000;
    public boolean calcMode = true;
    public boolean deletePracticeWorlds = true;
    public boolean postBlindSpawnChunks = false;
    public boolean caveSpawns = true;
    public boolean randomisePostBlindInventory = true;
    public boolean useSeedList = false;
    public String timerStart = "player_move";
    public String timerStop = "scenario_complete";
    public Map<String, Integer> practiceSlots = new HashMap<String, Integer>();
    public Map<String, List<List<String>>> practiceInventories = new HashMap<String, List<List<String>>>();
    public Map<String, String> keybinds = new HashMap<String, String>();

    public SpeedrunPracticeConfig() {
        practiceSlots.put("end", 0);
        practiceSlots.put("nether", 0);
        practiceSlots.put("postblind", 0);
        practiceSlots.put("overworld", 0);
        practiceSlots.put("stronghold", 0);
        for (String key : Arrays.asList("end", "nether", "overworld", "stronghold", "postblind")) {
            List<List<String>> slots = new ArrayList<List<String>>();
            slots.add(new ArrayList<String>());
            slots.add(new ArrayList<String>());
            slots.add(new ArrayList<String>());
            practiceInventories.put(key, slots);
        }
        keybinds.put("restartSame", "F6");
        keybinds.put("restartNew", "F7");
        keybinds.put("previousSeed", "F8");
        keybinds.put("saveCheckpoint", "F9");
        keybinds.put("loadCheckpoint", "F10");
        keybinds.put("stopPractice", "F11");
        keybinds.put("openMenu", "F5");
    }

    /** Loads (and migrates) the config; missing/corrupt files yield safe defaults. */
    public static SpeedrunPracticeConfig load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return new SpeedrunPracticeConfig();
        }
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Map<String, Object> raw = SimpleJson.parseObject(text);
            Map<String, Object> migrated = ConfigMigrator.migrate(raw, file.toString());
            return fromMap(migrated);
        } catch (Exception failure) {
            backupCorrupt(file);
            SpeedrunLogger.error("Invalid config, backed up and reset to defaults: " + failure.getMessage());
            return new SpeedrunPracticeConfig();
        }
    }

    public void save(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        Path file = configDir.resolve(FILE_NAME);
        Files.write(file, SimpleJson.toJson(toMap(), true).getBytes(StandardCharsets.UTF_8));
    }

    private static void backupCorrupt(Path file) {
        try {
            Path backup = file.resolveSibling(FILE_NAME + ".bak." + System.currentTimeMillis());
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            SpeedrunLogger.warn("Backed up corrupt config to " + backup.getFileName());
        } catch (IOException backupFailure) {
            SpeedrunLogger.error("Could not back up corrupt config", backupFailure);
        }
    }

    @SuppressWarnings("unchecked")
    static SpeedrunPracticeConfig fromMap(Map<String, Object> map) {
        SpeedrunPracticeConfig config = new SpeedrunPracticeConfig();
        config.schemaVersion = asInt(map.get("schemaVersion"), ConfigMigrator.CURRENT_SCHEMA);
        config.defaultMaxDist = asInt(map.get("defaultMaxDist"), config.defaultMaxDist);
        config.calcMode = asBool(map.get("calcMode"), config.calcMode);
        config.deletePracticeWorlds = asBool(map.get("deletePracticeWorlds"), config.deletePracticeWorlds);
        config.postBlindSpawnChunks = asBool(map.get("postBlindSpawnChunks"), config.postBlindSpawnChunks);
        config.caveSpawns = asBool(map.get("caveSpawns"), config.caveSpawns);
        config.randomisePostBlindInventory = asBool(map.get("randomisePostBlindInventory"), config.randomisePostBlindInventory);
        config.useSeedList = asBool(map.get("useSeedList"), config.useSeedList);
        config.timerStart = asString(map.get("timerStart"), config.timerStart);
        config.timerStop = asString(map.get("timerStop"), config.timerStop);
        Object slots = map.get("practiceSlots");
        if (slots instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) slots).entrySet()) {
                config.practiceSlots.put(String.valueOf(entry.getKey()), asInt(entry.getValue(), 0));
            }
        }
        Object inventories = map.get("practiceInventories");
        if (inventories instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) inventories).entrySet()) {
                List<List<String>> parsed = new ArrayList<List<String>>();
                if (entry.getValue() instanceof List) {
                    for (Object slot : (List<?>) entry.getValue()) {
                        List<String> strings = new ArrayList<String>();
                        if (slot instanceof List) {
                            for (Object item : (List<?>) slot) {
                                strings.add(String.valueOf(item));
                            }
                        }
                        parsed.add(strings);
                    }
                }
                config.practiceInventories.put(String.valueOf(entry.getKey()), parsed);
            }
        }
        Object keys = map.get("keybinds");
        if (keys instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) keys).entrySet()) {
                config.keybinds.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return config;
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("schemaVersion", schemaVersion);
        map.put("defaultMaxDist", defaultMaxDist);
        map.put("calcMode", calcMode);
        map.put("deletePracticeWorlds", deletePracticeWorlds);
        map.put("postBlindSpawnChunks", postBlindSpawnChunks);
        map.put("caveSpawns", caveSpawns);
        map.put("randomisePostBlindInventory", randomisePostBlindInventory);
        map.put("useSeedList", useSeedList);
        map.put("timerStart", timerStart);
        map.put("timerStop", timerStop);
        map.put("practiceSlots", practiceSlots);
        map.put("practiceInventories", practiceInventories);
        map.put("keybinds", keybinds);
        return map;
    }

    private static String asString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean asBool(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        return fallback;
    }
}
