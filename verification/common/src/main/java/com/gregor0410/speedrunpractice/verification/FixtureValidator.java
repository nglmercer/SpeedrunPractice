package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.util.SimpleJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Validates the canonical deterministic fixture envelope before a run starts. */
public final class FixtureValidator {
    private FixtureValidator() {
    }

    /**
     * Requires five verified rows and the fields needed by differential
     * structure/worldgen checks. This intentionally rejects the old empty
     * 26.3 placeholder file and the one-row 1.21.1 placeholder.
     */
    public static void validate(Path fixture, String expectedVersion) throws IOException {
        if (fixture == null || !Files.isRegularFile(fixture)) {
            throw new IOException("fixture does not exist: " + fixture);
        }
        Map<String, Object> root = SimpleJson.parseObject(
                new String(Files.readAllBytes(fixture), StandardCharsets.UTF_8));
        requireEquals(root.get("version"), expectedVersion, "version");
        Object rowsValue = root.get("seeds");
        if (!(rowsValue instanceof List)) {
            throw new IOException("fixture " + fixture + " must contain a seeds array");
        }
        List<?> rows = (List<?>) rowsValue;
        if (rows.size() < 5) {
            throw new IOException("fixture " + fixture + " has " + rows.size()
                    + " seeds; at least 5 are required");
        }
        for (int i = 0; i < rows.size(); i++) {
            Object rowValue = rows.get(i);
            if (!(rowValue instanceof Map)) {
                throw new IOException("fixture row " + i + " is not an object");
            }
            Map<?, ?> row = (Map<?, ?>) rowValue;
            if (!Boolean.TRUE.equals(row.get("verified"))) {
                throw new IOException("fixture row " + i + " is not marked verified");
            }
            if (!(row.get("seed") instanceof Number)) {
                throw new IOException("fixture row " + i + " has no numeric seed");
            }
            requireMap(row.get("spawn"), "row " + i + " spawn");
            requireMap(row.get("structures"), "row " + i + " structures");
            if (row.get("spawnBiome") == null && row.get("spawn_biome") == null) {
                throw new IOException("fixture row " + i + " has no spawn biome");
            }
            if (row.get("lavaAvailable") == null && row.get("lava") == null) {
                throw new IOException("fixture row " + i + " has no lava result");
            }
        }
    }

    private static void requireMap(Object value, String field) throws IOException {
        if (!(value instanceof Map)) {
            throw new IOException("fixture is missing object field " + field);
        }
    }

    private static void requireEquals(Object actual, String expected, String field) throws IOException {
        if (!expected.equals(String.valueOf(actual))) {
            throw new IOException("fixture " + field + " is " + actual + ", expected " + expected);
        }
    }
}
