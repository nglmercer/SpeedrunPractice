package com.gregor0410.speedrunpractice.common.seeds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Composable seed filters (plan section 17) evaluated against
 * {@link SeedAnalyzer} findings. Conventions for findings keys:
 * {@code biome.spawn}, {@code structure.<id>.distance}, {@code bastion.type},
 * {@code stronghold.ring}, {@code lava.available}.
 */
public final class SeedFilters {
    public static final String FIND_BIOME_SPAWN = "biome.spawn";
    public static final String FIND_STRUCTURE_PREFIX = "structure.";
    public static final String FIND_STRUCTURE_SUFFIX = ".distance";
    public static final String FIND_BASTION_TYPE = "bastion.type";
    public static final String FIND_STRONGHOLD_RING = "stronghold.ring";
    public static final String FIND_LAVA = "lava.available";

    private SeedFilters() {
    }

    public interface Filter {
        String id();

        boolean matches(long seed, SeedAnalyzer analyzer, SeedQuery query);
    }

    public static String findingString(Map<String, Object> findings, String key) {
        Object value = findings.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Long findingNumber(Map<String, Object> findings, String key) {
        Object value = findings.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    public static Boolean findingBoolean(Map<String, Object> findings, String key) {
        Object value = findings.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }

    public static final class BiomeFilter implements Filter {
        private final String biome;

        public BiomeFilter(String biome) {
            if (biome == null || biome.trim().isEmpty()) {
                throw new IllegalArgumentException("biome must not be empty");
            }
            this.biome = biome.trim();
        }

        @Override
        public String id() {
            return "biome:" + biome;
        }

        @Override
        public boolean matches(long seed, SeedAnalyzer analyzer, SeedQuery query) {
            return biome.equalsIgnoreCase(findingString(analyzer.analyze(seed, query).findings(), FIND_BIOME_SPAWN));
        }
    }

    public static final class StructureDistanceFilter implements Filter {
        private final String structureId;
        private final int min;
        private final int max;

        public StructureDistanceFilter(String structureId, int min, int max) {
            if (structureId == null || structureId.trim().isEmpty()) {
                throw new IllegalArgumentException("structure id must not be empty");
            }
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("need 0 <= min <= max");
            }
            this.structureId = structureId.trim();
            this.min = min;
            this.max = max;
        }

        @Override
        public String id() {
            return "structure:" + structureId + ":" + min + "-" + max;
        }

        @Override
        public boolean matches(long seed, SeedAnalyzer analyzer, SeedQuery query) {
            Long distance = findingNumber(analyzer.analyze(seed, query).findings(),
                    FIND_STRUCTURE_PREFIX + structureId + FIND_STRUCTURE_SUFFIX);
            return distance != null && distance >= min && distance <= max;
        }
    }

    public static final class BastionTypeFilter implements Filter {
        private final String type;

        public BastionTypeFilter(String type) {
            if (type == null || type.trim().isEmpty()) {
                throw new IllegalArgumentException("bastion type must not be empty");
            }
            this.type = type.trim().toLowerCase();
        }

        @Override
        public String id() {
            return "bastion_type:" + type;
        }

        @Override
        public boolean matches(long seed, SeedAnalyzer analyzer, SeedQuery query) {
            return type.equalsIgnoreCase(findingString(analyzer.analyze(seed, query).findings(), FIND_BASTION_TYPE));
        }
    }

    public static final class StrongholdRingFilter implements Filter {
        private final int ring;

        public StrongholdRingFilter(int ring) {
            if (ring < 1) {
                throw new IllegalArgumentException("ring must be >= 1");
            }
            this.ring = ring;
        }

        @Override
        public String id() {
            return "stronghold_ring:" + ring;
        }

        @Override
        public boolean matches(long seed, SeedAnalyzer analyzer, SeedQuery query) {
            Long actual = findingNumber(analyzer.analyze(seed, query).findings(), FIND_STRONGHOLD_RING);
            return actual != null && actual.intValue() == ring;
        }
    }

    public static final class LavaFilter implements Filter {
        @Override
        public String id() {
            return "lava_available";
        }

        @Override
        public boolean matches(long seed, SeedAnalyzer analyzer, SeedQuery query) {
            return Boolean.TRUE.equals(findingBoolean(analyzer.analyze(seed, query).findings(), FIND_LAVA));
        }
    }

    /** Logical AND of nested filters. */
    public static final class CombinedFilter implements Filter {
        private final List<Filter> filters;

        public CombinedFilter(List<Filter> filters) {
            if (filters == null || filters.isEmpty()) {
                throw new IllegalArgumentException("combined filter needs at least one child");
            }
            this.filters = Collections.unmodifiableList(new ArrayList<Filter>(filters));
        }

        @Override
        public String id() {
            StringBuilder out = new StringBuilder("all(");
            for (int i = 0; i < filters.size(); i++) {
                if (i > 0) {
                    out.append('+');
                }
                out.append(filters.get(i).id());
            }
            return out.append(')').toString();
        }

        @Override
        public boolean matches(long seed, SeedAnalyzer analyzer, SeedQuery query) {
            for (Filter filter : filters) {
                if (!filter.matches(seed, analyzer, query)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Logical OR of nested filters. */
    public static final class AnyFilter implements Filter {
        private final List<Filter> filters;

        public AnyFilter(List<Filter> filters) {
            if (filters == null || filters.isEmpty()) {
                throw new IllegalArgumentException("any filter needs at least one child");
            }
            this.filters = Collections.unmodifiableList(new ArrayList<Filter>(filters));
        }

        @Override
        public String id() {
            StringBuilder out = new StringBuilder("any(");
            for (int i = 0; i < filters.size(); i++) {
                if (i > 0) {
                    out.append('|');
                }
                out.append(filters.get(i).id());
            }
            return out.append(')').toString();
        }

        @Override
        public boolean matches(long seed, SeedAnalyzer analyzer, SeedQuery query) {
            for (Filter filter : filters) {
                if (filter.matches(seed, analyzer, query)) {
                    return true;
                }
            }
            return false;
        }
    }
}
