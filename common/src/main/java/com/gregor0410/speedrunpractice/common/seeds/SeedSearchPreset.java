package com.gregor0410.speedrunpractice.common.seeds;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.util.SimpleJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Saved seed-search preset ({@code seeds/searches/<name>.json}, plan section
 * 5): parses the JSON form into a {@link SeedQuery}, the stage-A
 * {@link SeedFilters} proving each hit, and the scan bounds.
 *
 * <pre>
 * {
 *   "version": "1.16.1",
 *   "biome": "minecraft:plains",
 *   "structures": {"minecraft:village": 1500, "minecraft:stronghold": 2000},
 *   "bastionType": "treasure",
 *   "strongholdRing": 1,
 *   "lava": false,
 *   "startSeed": 0,
 *   "maxResults": 10,
 *   "maxAttempts": 100000,
 *   "constraints": {"custom.key": "value"}
 * }
 * </pre>
 *
 * <p>Only {@code structures} entries are required to make a search useful;
 * every field is optional. Biome matching stays inside the analyzer (it
 * compares namespace-stripped ids); the built filters re-check structure
 * distances, bastion type and stronghold ring against the analyzer findings
 * so every result records which filters it matched.
 */
public final class SeedSearchPreset {
    public static final int DEFAULT_MAX_RESULTS = 10;
    public static final long DEFAULT_MAX_ATTEMPTS = 1000000L;

    private SeedSearchPreset() {
    }

    /** The four bastion subtypes, shared by presets, queries and analyzers. */
    public static boolean isBastionType(String type) {
        if (type == null) {
            return false;
        }
        String clean = type.trim().toLowerCase();
        return "housing".equals(clean) || "stables".equals(clean)
                || "treasure".equals(clean) || "bridge".equals(clean);
    }

    /**
     * Parses one preset. {@code defaultVersion} (normally the running game
     * version) applies when the JSON omits {@code version}. Failures are
     * {@link PracticeException} with a user-facing message naming the preset.
     */
    @SuppressWarnings("unchecked")
    public static ParsedPreset parse(String name, String json, GameVersion defaultVersion)
            throws PracticeException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("preset name must not be empty");
        }
        if (defaultVersion == null) {
            throw new IllegalArgumentException("defaultVersion must not be null");
        }
        Object parsed;
        try {
            parsed = SimpleJson.parse(json == null ? "" : json);
        } catch (RuntimeException bad) {
            throw failure(name, "not valid JSON: " + bad.getMessage());
        }
        if (!(parsed instanceof Map)) {
            throw failure(name, "must be a JSON object");
        }
        Map<String, Object> root = (Map<String, Object>) parsed;

        GameVersion version = defaultVersion;
        if (root.containsKey("version")) {
            try {
                version = GameVersion.parse(String.valueOf(root.get("version")));
            } catch (IllegalArgumentException bad) {
                throw failure(name, bad.getMessage());
            }
        }
        SeedQuery.Builder query = SeedQuery.builder().version(version);
        List<SeedFilters.Filter> filters = new ArrayList<SeedFilters.Filter>();

        if (root.containsKey("biome")) {
            String biome = stringField(name, root, "biome");
            query.requireBiome(biome);
        }
        if (root.containsKey("structures")) {
            Object rawStructures = root.get("structures");
            if (!(rawStructures instanceof Map)) {
                throw failure(name, "\"structures\" must be an object of id to max-distance");
            }
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawStructures).entrySet()) {
                String id = String.valueOf(entry.getKey());
                int distance = intField(name, "\"structures\".\"" + id + "\"", entry.getValue());
                if (distance < 0) {
                    throw failure(name, "distance for \"" + id + "\" must be >= 0");
                }
                try {
                    query.requireStructure(id, distance);
                } catch (IllegalArgumentException bad) {
                    throw failure(name, bad.getMessage());
                }
                filters.add(new SeedFilters.StructureDistanceFilter(id, 0, distance));
            }
        }
        if (root.containsKey("bastionType")) {
            String type = stringField(name, root, "bastionType").toLowerCase();
            if (!isBastionType(type)) {
                throw failure(name, "unknown \"bastionType\" \"" + type
                        + "\" (housing, stables, treasure or bridge)");
            }
            query.bastionType(type);
            filters.add(new SeedFilters.BastionTypeFilter(type));
        }
        if (root.containsKey("strongholdRing")) {
            int ring = intField(name, "\"strongholdRing\"", root.get("strongholdRing"));
            if (ring < 1) {
                throw failure(name, "\"strongholdRing\" must be >= 1");
            }
            query.strongholdRing(ring);
            filters.add(new SeedFilters.StrongholdRingFilter(ring));
        }
        if (root.containsKey("lava") && booleanField(name, root, "lava")) {
            query.requireLava();
        }
        if (root.containsKey("maxDistance")) {
            int distance = intField(name, "\"maxDistance\"", root.get("maxDistance"));
            if (distance < 0) {
                throw failure(name, "\"maxDistance\" must be >= 0");
            }
            query.maxDistance(distance);
        }
        if (root.containsKey("constraints")) {
            Object rawConstraints = root.get("constraints");
            if (!(rawConstraints instanceof Map)) {
                throw failure(name, "\"constraints\" must be an object of key to value");
            }
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawConstraints).entrySet()) {
                query.constraint(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }

        Long startSeed = null;
        if (root.containsKey("startSeed")) {
            startSeed = longField(name, "\"startSeed\"", root.get("startSeed"));
        }
        int maxResults = DEFAULT_MAX_RESULTS;
        if (root.containsKey("maxResults")) {
            maxResults = intField(name, "\"maxResults\"", root.get("maxResults"));
            if (maxResults < 1) {
                throw failure(name, "\"maxResults\" must be >= 1");
            }
        }
        long maxAttempts = DEFAULT_MAX_ATTEMPTS;
        if (root.containsKey("maxAttempts")) {
            maxAttempts = longField(name, "\"maxAttempts\"", root.get("maxAttempts"));
            if (maxAttempts < 1) {
                throw failure(name, "\"maxAttempts\" must be >= 1");
            }
        }
        return new ParsedPreset(name, query.build(), filters, startSeed, maxResults, maxAttempts);
    }

    private static PracticeException failure(String name, String detail) {
        return new PracticeException("Seed search preset \"" + name + "\": " + detail,
                "Search preset \"" + name + "\" is invalid: " + detail + ".");
    }

    private static String stringField(String name, Map<String, Object> root, String key)
            throws PracticeException {
        Object value = root.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw failure(name, "\"" + key + "\" must be a non-empty string");
        }
        return ((String) value).trim();
    }

    private static int intField(String name, String label, Object value) throws PracticeException {
        long parsed = longField(name, label, value);
        if (parsed > Integer.MAX_VALUE) {
            throw failure(name, label + " is too large");
        }
        return (int) parsed;
    }

    private static long longField(String name, String label, Object value) throws PracticeException {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException bad) {
                throw failure(name, label + " must be a number");
            }
        }
        throw failure(name, label + " must be a number");
    }

    private static boolean booleanField(String name, Map<String, Object> root, String key)
            throws PracticeException {
        Object value = root.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String clean = ((String) value).trim().toLowerCase();
            if ("true".equals(clean) || "false".equals(clean)) {
                return Boolean.parseBoolean(clean);
            }
        }
        throw failure(name, "\"" + key + "\" must be true or false");
    }

    /** One parsed preset: query, proving filters and scan bounds. */
    public static final class ParsedPreset {
        private final String name;
        private final SeedQuery query;
        private final List<SeedFilters.Filter> filters;
        private final Long startSeed;
        private final int maxResults;
        private final long maxAttempts;

        private ParsedPreset(String name, SeedQuery query, List<SeedFilters.Filter> filters,
                             Long startSeed, int maxResults, long maxAttempts) {
            this.name = name;
            this.query = query;
            this.filters = Collections.unmodifiableList(new ArrayList<SeedFilters.Filter>(filters));
            this.startSeed = startSeed;
            this.maxResults = maxResults;
            this.maxAttempts = maxAttempts;
        }

        public String name() {
            return name;
        }

        public SeedQuery query() {
            return query;
        }

        public List<SeedFilters.Filter> filters() {
            return filters;
        }

        /** Null when the preset omits {@code startSeed} (caller picks one). */
        public Long startSeed() {
            return startSeed;
        }

        public int maxResults() {
            return maxResults;
        }

        public long maxAttempts() {
            return maxAttempts;
        }
    }
}
