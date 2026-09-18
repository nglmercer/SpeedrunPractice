package com.gregor0410.speedrunpractice.common.scenario;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.util.SimpleJson;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads data-driven scenarios. Bad JSON yields readable errors naming file,
 * field and expectation; {@link #loadAll(Path)} skips bad files and never
 * throws, so a user typo can never crash the game (plan section 13).
 */
public final class ScenarioLoader {
    private ScenarioLoader() {
    }

    public static ScenarioDefinition loadFromJson(String json, String origin)
            throws PracticeException.ScenarioLoadException {
        Map<String, Object> map;
        try {
            map = SimpleJson.parseObject(json);
        } catch (SimpleJson.JsonException bad) {
            throw new PracticeException.ScenarioLoadException(origin + ": invalid JSON: " + bad.getMessage(),
                    origin + ": this file is not valid JSON: " + bad.getMessage());
        }
        return ScenarioDefinition.fromMap(map, origin);
    }

    public static ScenarioDefinition loadFromFile(Path file) throws PracticeException.ScenarioLoadException {
        String origin = file.getFileName().toString();
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            return loadFromJson(json, origin);
        } catch (IOException bad) {
            throw new PracticeException.ScenarioLoadException(origin + ": cannot read file: " + bad.getMessage(),
                    origin + ": could not read this scenario file.");
        }
    }

    /** Loads every {@code *.json} in the directory; invalid files are skipped with a warning. */
    public static Map<String, ScenarioDefinition> loadAll(Path dir) {
        Map<String, ScenarioDefinition> definitions = new LinkedHashMap<String, ScenarioDefinition>();
        try {
            Files.createDirectories(dir);
        } catch (IOException bad) {
            SpeedrunLogger.warn("Cannot create scenario directory " + dir + ": " + bad.getMessage());
            return definitions;
        }
        DirectoryStream<Path> stream = null;
        try {
            stream = Files.newDirectoryStream(dir, "*.json");
            for (Path file : stream) {
                try {
                    ScenarioDefinition definition = loadFromFile(file);
                    if (definitions.containsKey(definition.id().value())) {
                        SpeedrunLogger.warn("Skipping " + file.getFileName()
                                + ": duplicate scenario id \"" + definition.id() + "\"");
                    } else {
                        definitions.put(definition.id().value(), definition);
                    }
                } catch (PracticeException.ScenarioLoadException bad) {
                    SpeedrunLogger.warn("Skipping " + file.getFileName() + ": " + bad.getUserMessage());
                }
            }
        } catch (IOException bad) {
            SpeedrunLogger.warn("Cannot list scenario directory " + dir + ": " + bad.getMessage());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Best effort.
                }
            }
        }
        return Collections.unmodifiableMap(definitions);
    }
}
