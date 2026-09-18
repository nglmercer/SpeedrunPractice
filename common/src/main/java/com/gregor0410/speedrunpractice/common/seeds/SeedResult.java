package com.gregor0410.speedrunpractice.common.seeds;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One search hit: seed, version, matched filters, structure locations,
 * verification state and timestamp (plan section 18).
 */
public final class SeedResult {
    /** Stage B outcome. */
    public enum VerificationState {
        UNVERIFIED,
        VERIFIED,
        FAILED
    }

    private final long seed;
    private final GameVersion version;
    private final List<String> matchedFilters;
    private final Map<String, StructureAdapter.StructureLocation> structureLocations;
    private final VerificationState verificationState;
    private final long searchTimestampMs;

    public SeedResult(long seed, GameVersion version, List<String> matchedFilters,
                      Map<String, StructureAdapter.StructureLocation> structureLocations,
                      VerificationState verificationState, long searchTimestampMs) {
        if (version == null || verificationState == null) {
            throw new IllegalArgumentException("version and verificationState must not be null");
        }
        this.seed = seed;
        this.version = version;
        this.matchedFilters = matchedFilters == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(matchedFilters));
        this.structureLocations = structureLocations == null
                ? Collections.<String, StructureAdapter.StructureLocation>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, StructureAdapter.StructureLocation>(structureLocations));
        this.verificationState = verificationState;
        this.searchTimestampMs = searchTimestampMs;
    }

    public long seed() {
        return seed;
    }

    public GameVersion version() {
        return version;
    }

    public List<String> matchedFilters() {
        return matchedFilters;
    }

    public Map<String, StructureAdapter.StructureLocation> structureLocations() {
        return structureLocations;
    }

    public VerificationState verificationState() {
        return verificationState;
    }

    public long searchTimestampMs() {
        return searchTimestampMs;
    }

    public SeedResult withVerificationState(VerificationState state) {
        return new SeedResult(seed, version, matchedFilters, structureLocations, state, searchTimestampMs);
    }

    /** A stage-A survivor awaiting verification. */
    public static final class SeedCandidate {
        private final long seed;
        private final String stage;
        private final long testedAtMs;

        public SeedCandidate(long seed, String stage, long testedAtMs) {
            this.seed = seed;
            this.stage = stage;
            this.testedAtMs = testedAtMs;
        }

        public long seed() {
            return seed;
        }

        public String stage() {
            return stage;
        }

        public long testedAtMs() {
            return testedAtMs;
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("seed", seed);
        map.put("minecraftVersion", version.versionString());
        map.put("matchedFilters", matchedFilters);
        Map<String, Object> locations = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, StructureAdapter.StructureLocation> entry : structureLocations.entrySet()) {
            Map<String, Object> pos = new LinkedHashMap<String, Object>();
            pos.put("x", entry.getValue().position().blockX());
            pos.put("y", entry.getValue().position().blockY());
            pos.put("z", entry.getValue().position().blockZ());
            locations.put(entry.getKey(), pos);
        }
        map.put("structureLocations", locations);
        map.put("verificationState", verificationState.name());
        map.put("searchTimestamp", searchTimestampMs);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static SeedResult fromMap(Map<String, Object> map) {
        long seed = ((Number) map.get("seed")).longValue();
        GameVersion version = GameVersion.parse(String.valueOf(map.get("minecraftVersion")));
        List<String> filters = new ArrayList<String>();
        Object rawFilters = map.get("matchedFilters");
        if (rawFilters instanceof List) {
            for (Object filter : (List<?>) rawFilters) {
                filters.add(String.valueOf(filter));
            }
        }
        Map<String, StructureAdapter.StructureLocation> locations =
                new LinkedHashMap<String, StructureAdapter.StructureLocation>();
        Object rawLocations = map.get("structureLocations");
        if (rawLocations instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawLocations).entrySet()) {
                Map<String, Object> pos = (Map<String, Object>) entry.getValue();
                locations.put(String.valueOf(entry.getKey()), new StructureAdapter.StructureLocation(
                        String.valueOf(entry.getKey()),
                        new PracticePosition(((Number) pos.get("x")).doubleValue(),
                                ((Number) pos.get("y")).doubleValue(), ((Number) pos.get("z")).doubleValue()),
                        null));
            }
        }
        VerificationState state = VerificationState.valueOf(String.valueOf(map.get("verificationState")));
        long timestamp = ((Number) map.get("searchTimestamp")).longValue();
        return new SeedResult(seed, version, filters, locations, state, timestamp);
    }
}
