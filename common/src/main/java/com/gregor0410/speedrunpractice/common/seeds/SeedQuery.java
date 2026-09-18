package com.gregor0410.speedrunpractice.common.seeds;

import com.gregor0410.speedrunpractice.common.api.GameVersion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a wanted seed looks like (plan section 16). Actual world math lives in
 * the version {@link SeedAnalyzer}; this is pure data plus a builder.
 */
public final class SeedQuery {
    private final GameVersion version;
    private final String requiredBiome;
    private final Map<String, Integer> requiredStructures;
    private final int maxDistance;
    private final Map<String, String> constraints;

    private SeedQuery(Builder builder) {
        this.version = builder.version;
        this.requiredBiome = builder.requiredBiome;
        this.requiredStructures = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(builder.requiredStructures));
        this.maxDistance = builder.maxDistance;
        this.constraints = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.constraints));
    }

    public static Builder builder() {
        return new Builder();
    }

    public GameVersion version() {
        return version;
    }

    /** Null means "any biome". */
    public String requiredBiome() {
        return requiredBiome;
    }

    /** Structure id -> max distance from the search origin. */
    public Map<String, Integer> requiredStructures() {
        return requiredStructures;
    }

    public int maxDistance() {
        return maxDistance;
    }

    /** Free-form extra constraints interpreted by filters/analyzers. */
    public Map<String, String> constraints() {
        return constraints;
    }

    public static final class Builder {
        private GameVersion version = GameVersion.MC_1_16_1;
        private String requiredBiome;
        private final Map<String, Integer> requiredStructures = new LinkedHashMap<String, Integer>();
        private int maxDistance = Integer.MAX_VALUE;
        private final Map<String, String> constraints = new LinkedHashMap<String, String>();

        private Builder() {
        }

        public Builder version(GameVersion version) {
            if (version == null) {
                throw new IllegalArgumentException("version must not be null");
            }
            this.version = version;
            return this;
        }

        public Builder requireBiome(String biome) {
            this.requiredBiome = biome;
            return this;
        }

        public Builder requireStructure(String structureId) {
            return requireStructure(structureId, Integer.MAX_VALUE);
        }

        public Builder requireStructure(String structureId, int maxDistance) {
            if (structureId == null || structureId.trim().isEmpty()) {
                throw new IllegalArgumentException("structure id must not be empty");
            }
            if (maxDistance < 0) {
                throw new IllegalArgumentException("max distance must be >= 0");
            }
            requiredStructures.put(structureId.trim(), maxDistance);
            return this;
        }

        public Builder maxDistance(int maxDistance) {
            if (maxDistance < 0) {
                throw new IllegalArgumentException("max distance must be >= 0");
            }
            this.maxDistance = maxDistance;
            return this;
        }

        public Builder constraint(String key, String value) {
            if (key == null || value == null) {
                throw new IllegalArgumentException("constraint key/value must not be null");
            }
            constraints.put(key, value);
            return this;
        }

        public SeedQuery build() {
            return new SeedQuery(this);
        }
    }
}
