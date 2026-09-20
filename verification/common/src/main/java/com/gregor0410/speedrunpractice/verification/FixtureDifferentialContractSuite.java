package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedFilters;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.util.SimpleJson;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verification-only analyzer differential: reviewed fixture rows are checked
 * against both live structure lookup and the version seed analyzer.
 */
public final class FixtureDifferentialContractSuite {
    private static final int STRUCTURE_RADIUS = 10000;

    private FixtureDifferentialContractSuite() {
    }

    public static Result run(MinecraftAdapter adapter, String resource) {
        Result result = new Result();
        try {
            Map<String, Object> root = load(resource);
            Object rowsValue = root.get("seeds");
            if (!(rowsValue instanceof List)) {
                throw new IOException("fixture has no seeds array");
            }
            for (Object value : (List<?>) rowsValue) {
                checkRow(result, adapter, castMap(value, "fixture row"));
            }
        } catch (Exception failure) {
            result.record(false, "fixture load: " + message(failure));
        }
        SpeedrunLogger.info("Verification seed.differential "
                + (result.matches() == result.total() && result.total() > 0 ? "passed" : "failed")
                + " (" + result.matches() + "/" + result.total() + ")");
        return result;
    }

    private static void checkRow(Result result, MinecraftAdapter adapter, Map<?, ?> row) {
        long seed = 0L;
        PracticeWorld world = null;
        try {
            seed = number(row.get("seed"), "seed");
            world = adapter.worlds().createPracticeWorld(seed,
                    WorldAdapter.PracticeWorldOptions.builder(PracticeDimension.OVERWORLD).build());
            PracticePosition expectedSpawn = position(castMap(row.get("spawn"), "spawn"));
            PracticePosition actualSpawn = adapter.worlds().spawnPosition(world);
            requirePosition(actualSpawn, expectedSpawn, "spawn", seed);

            String expectedBiome = string(row.get("spawnBiome"), row.get("spawn_biome"), "spawnBiome");
            Map<?, ?> structures = castMap(row.get("structures"), "structures");
            SeedQuery.Builder queryBuilder = SeedQuery.builder().version(adapter.version())
                    .requireBiome(expectedBiome);
            String expectedBastionType = null;
            for (Map.Entry<?, ?> entry : structures.entrySet()) {
                String id = String.valueOf(entry.getKey());
                queryBuilder.requireStructure(id, STRUCTURE_RADIUS);
                Map<?, ?> expected = castMap(entry.getValue(), "structure " + id);
                Object type = expected.get("bastionType");
                if (type != null) {
                    expectedBastionType = String.valueOf(type);
                }
            }
            SeedQuery query = queryBuilder.build();
            SeedAnalyzer.SeedAnalysis analysis = adapter.seeds().analyze(seed, query);
            if (!analysis.matches()) {
                throw new IllegalStateException("analyzer mismatch " + analysis.findings());
            }
            if (expectedBastionType != null) {
                SeedAnalyzer.SeedAnalysis typed = adapter.seeds().analyze(seed,
                        queryBuilder.bastionType(expectedBastionType).build());
                if (!typed.matches()) {
                    throw new IllegalStateException("typed analyzer mismatch for " + seed);
                }
            }
            if (!expectedBiome.equalsIgnoreCase(String.valueOf(
                    analysis.findings().get(SeedFilters.FIND_BIOME_SPAWN)))) {
                throw new IllegalStateException("spawn biome mismatch: expected " + expectedBiome
                        + ", analyzer=" + analysis.findings().get(SeedFilters.FIND_BIOME_SPAWN));
            }
            for (Map.Entry<?, ?> entry : structures.entrySet()) {
                String id = String.valueOf(entry.getKey());
                Map<?, ?> expected = castMap(entry.getValue(), "structure " + id);
                PracticePosition expectedPosition = position(expected);
                StructureAdapter.StructureLocation live = adapter.structures().locateNearest(world,
                        StructureAdapter.StructureQuery.builder(id).radius(STRUCTURE_RADIUS).build())
                        .orElseThrow(() -> new IllegalStateException("live structure missing " + id));
                requireHorizontalPosition(live.position(), expectedPosition, id + " live", seed);
                Object predictedValue = analysis.findings().get("location." + id);
                if (!(predictedValue instanceof PracticePosition)) {
                    throw new IllegalStateException("analyzer has no location for " + id);
                }
                try {
                    requireHorizontalPosition((PracticePosition) predictedValue, live.position(),
                            id + " prediction", seed);
                } catch (IllegalStateException mismatch) {
                    throw new IllegalStateException(mismatch.getMessage()
                            + "; liveMetadata=" + live.metadata()
                            + "; findings=" + analysis.findings());
                }
                String expectedType = optionalString(expected.get("bastionType"));
                if (expectedType != null) {
                    String liveType = live.metadata().get(StructureAdapter.StructureLocation.BASTION_TYPE_KEY);
                    String predictedType = String.valueOf(
                            analysis.findings().get(SeedFilters.FIND_BASTION_TYPE));
                    if (liveType == null) {
                        throw new IllegalStateException("live bastion type missing for " + seed);
                    }
                    if (!expectedType.equalsIgnoreCase(liveType)
                            || !expectedType.equalsIgnoreCase(predictedType)) {
                        throw new IllegalStateException("bastion type mismatch for " + seed
                                + ": expected " + expectedType + ", live=" + liveType
                                + ", analyzer=" + predictedType);
                    }
                }
            }

            boolean expectedLava = bool(row.get("lavaAvailable"), row.get("lava"));
            // The reviewed row's lava flag is the real generated-world truth;
            // the live analyzer's Stage-B result must agree with it.
            SeedQuery.Builder stageB = SeedQuery.builder().version(adapter.version())
                    .requireBiome(expectedBiome).requireLava();
            for (Map.Entry<?, ?> entry : structures.entrySet()) {
                stageB.requireStructure(String.valueOf(entry.getKey()), STRUCTURE_RADIUS);
            }
            if (expectedBastionType != null) {
                stageB.bastionType(expectedBastionType);
            }
            boolean predictedLava = adapter.seeds().analyze(seed, stageB.build()).matches();
            if (predictedLava != expectedLava) {
                throw new IllegalStateException("lava mismatch: expected " + expectedLava
                        + ", analyzer=" + predictedLava);
            }
            result.record(true, "seed=" + seed);
        } catch (Exception failure) {
            result.record(false, "seed=" + seed + " " + message(failure));
        } finally {
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception failure) {
                    SpeedrunLogger.warn("Differential cleanup failed for " + seed + ": "
                            + failure.getMessage());
                }
            }
        }
    }

    private static Map<String, Object> load(String resource) throws IOException {
        InputStream input = FixtureDifferentialContractSuite.class.getResourceAsStream(resource);
        if (input == null) {
            throw new IOException("missing fixture resource " + resource);
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                bytes.write(buffer, 0, read);
            }
            return SimpleJson.parseObject(new String(bytes.toByteArray(), "UTF-8"));
        } finally {
            input.close();
        }
    }

    private static Map<?, ?> castMap(Object value, String field) throws IOException {
        if (!(value instanceof Map)) {
            throw new IOException(field + " is not an object");
        }
        return (Map<?, ?>) value;
    }

    private static PracticePosition position(Map<?, ?> value) throws IOException {
        return new PracticePosition(number(value.get("x"), "x"), number(value.get("y"), "y"),
                number(value.get("z"), "z"));
    }

    private static void requirePosition(PracticePosition actual, PracticePosition expected,
                                        String field, long seed) {
        if (actual == null || actual.blockX() != expected.blockX()
                || actual.blockY() != expected.blockY() || actual.blockZ() != expected.blockZ()) {
            throw new IllegalStateException(field + " mismatch for " + seed + ": expected "
                    + expected + ", actual " + actual);
        }
    }

    private static void requireHorizontalPosition(PracticePosition actual, PracticePosition expected,
                                                  String field, long seed) {
        if (actual == null || actual.blockX() != expected.blockX()
                || actual.blockZ() != expected.blockZ()) {
            throw new IllegalStateException(field + " mismatch for " + seed + ": expected "
                    + expected + ", actual " + actual);
        }
    }

    private static long number(Object value, String field) throws IOException {
        if (!(value instanceof Number)) {
            throw new IOException(field + " is not numeric");
        }
        return ((Number) value).longValue();
    }

    private static boolean bool(Object primary, Object fallback) throws IOException {
        Object value = primary == null ? fallback : primary;
        if (!(value instanceof Boolean)) {
            throw new IOException("lavaAvailable is not boolean");
        }
        return ((Boolean) value).booleanValue();
    }

    private static String string(Object primary, Object fallback, String field) throws IOException {
        Object value = primary == null ? fallback : primary;
        if (value == null) {
            throw new IOException(field + " is missing");
        }
        return String.valueOf(value);
    }

    private static String optionalString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String message(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + ": "
                + (message == null ? "no message" : message.replace('\n', ' ').replace(',', ';'));
    }

    public static final class Result {
        private int matches;
        private int total;
        private final List<String> evidence = new ArrayList<String>();

        private void record(boolean passed, String detail) {
            total++;
            if (passed) {
                matches++;
                evidence.add("seed.differential." + detail);
            } else {
                evidence.add("seed.differential=FAILED{" + detail + "}");
            }
        }

        public int matches() {
            return matches;
        }

        public int total() {
            return total;
        }

        public List<String> evidence() {
            return new ArrayList<String>(evidence);
        }
    }
}
