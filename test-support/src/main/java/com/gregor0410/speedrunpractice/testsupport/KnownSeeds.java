package com.gregor0410.speedrunpractice.testsupport;

import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.util.SimpleJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Known-seed tables from {@code test-data/<version>/} (plan section 29).
 * Tables carry a {@code verified} flag; rows stay {@code false} until checked
 * against a live game of that version.
 */
public final class KnownSeeds {
    /** Expected structure position for one seed. */
    public static final class StructureExpectation {
        private final long seed;
        private final String structure;
        private final PracticePosition position;
        private final boolean verified;

        public StructureExpectation(long seed, String structure, PracticePosition position, boolean verified) {
            this.seed = seed;
            this.structure = structure;
            this.position = position;
            this.verified = verified;
        }

        public long seed() {
            return seed;
        }

        public String structure() {
            return structure;
        }

        public PracticePosition position() {
            return position;
        }

        public boolean verified() {
            return verified;
        }
    }

    /** Smoke-test row: scenario id + seed + expected facts. */
    public static final class ScenarioExpectation {
        private final String id;
        private final String type;
        private final long seed;
        private final Map<String, String> expect;

        public ScenarioExpectation(String id, String type, long seed, Map<String, String> expect) {
            this.id = id;
            this.type = type;
            this.seed = seed;
            this.expect = Collections.unmodifiableMap(new LinkedHashMap<String, String>(expect));
        }

        public String id() {
            return id;
        }

        public String type() {
            return type;
        }

        public long seed() {
            return seed;
        }

        public Map<String, String> expect() {
            return expect;
        }
    }

    private final Map<String, List<StructureExpectation>> structures = new LinkedHashMap<String, List<StructureExpectation>>();
    private final Map<String, List<ScenarioExpectation>> scenarios = new LinkedHashMap<String, List<ScenarioExpectation>>();

    /** Loads every version directory present under the root; missing files are skipped. */
    public static KnownSeeds load(Path testDataRoot) throws IOException {
        KnownSeeds known = new KnownSeeds();
        if (!Files.isDirectory(testDataRoot)) {
            return known;
        }
        for (Path version : Files.newDirectoryStream(testDataRoot)) {
            if (!Files.isDirectory(version)) {
                continue;
            }
            String name = version.getFileName().toString();
            Path structuresFile = version.resolve("structures.json");
            if (Files.exists(structuresFile)) {
                known.structures.put(name, readStructures(structuresFile));
            }
            Path scenariosFile = version.resolve("scenarios.json");
            if (Files.exists(scenariosFile)) {
                known.scenarios.put(name, readScenarios(scenariosFile));
            }
        }
        return known;
    }

    /** Null when the table has no row for this seed+structure. */
    public PracticePosition expectedStructure(String version, long seed, String structure) {
        List<StructureExpectation> rows = structures.get(version);
        if (rows == null) {
            return null;
        }
        for (StructureExpectation row : rows) {
            if (row.seed() == seed && row.structure().equals(structure)) {
                return row.position();
            }
        }
        return null;
    }

    public List<StructureExpectation> structures(String version) {
        List<StructureExpectation> rows = structures.get(version);
        return rows == null ? Collections.<StructureExpectation>emptyList() : Collections.unmodifiableList(rows);
    }

    public List<ScenarioExpectation> scenarios(String version) {
        List<ScenarioExpectation> rows = scenarios.get(version);
        return rows == null ? Collections.<ScenarioExpectation>emptyList() : Collections.unmodifiableList(rows);
    }

    @SuppressWarnings("unchecked")
    private static List<StructureExpectation> readStructures(Path file) throws IOException {
        Map<String, Object> root = SimpleJson.parseObject(read(file));
        List<StructureExpectation> out = new ArrayList<StructureExpectation>();
        Object rawSeeds = root.get("seeds");
        if (!(rawSeeds instanceof List)) {
            return out;
        }
        for (Object row : (List<?>) rawSeeds) {
            Map<String, Object> seedRow = (Map<String, Object>) row;
            long seed = ((Number) seedRow.get("seed")).longValue();
            boolean verified = Boolean.TRUE.equals(seedRow.get("verified"));
            Object rawStructures = seedRow.get("structures");
            if (!(rawStructures instanceof Map)) {
                continue;
            }
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawStructures).entrySet()) {
                Map<String, Object> pos = (Map<String, Object>) entry.getValue();
                out.add(new StructureExpectation(seed, String.valueOf(entry.getKey()),
                        new PracticePosition(((Number) pos.get("x")).doubleValue(),
                                ((Number) pos.get("y")).doubleValue(), ((Number) pos.get("z")).doubleValue()),
                        verified));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<ScenarioExpectation> readScenarios(Path file) throws IOException {
        Map<String, Object> root = SimpleJson.parseObject(read(file));
        List<ScenarioExpectation> out = new ArrayList<ScenarioExpectation>();
        Object rawScenarios = root.get("scenarios");
        if (!(rawScenarios instanceof List)) {
            return out;
        }
        for (Object row : (List<?>) rawScenarios) {
            Map<String, Object> scenarioRow = (Map<String, Object>) row;
            Map<String, String> expect = new LinkedHashMap<String, String>();
            Object rawExpect = scenarioRow.get("expect");
            if (rawExpect instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawExpect).entrySet()) {
                    expect.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            out.add(new ScenarioExpectation(String.valueOf(scenarioRow.get("id")),
                    String.valueOf(scenarioRow.get("type")),
                    ((Number) scenarioRow.get("seed")).longValue(), expect));
        }
        return out;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
