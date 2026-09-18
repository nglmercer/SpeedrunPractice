package com.gregor0410.speedrunpractice.common.seeds;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Version-side seed math (plan section 16). Implementations live in the
 * version modules; shared code only consumes findings.
 */
public interface SeedAnalyzer {
    SeedAnalysis analyze(long seed, SeedQuery query);

    boolean matches(long seed, SeedQuery query);

    /**
     * Whether this analyzer really verifies lava-constrained queries
     * (chunk-generating Stage-B). Versions without it mismatch lava
     * queries, and preset startup fails fast with a readable error
     * instead of running a doomed search.
     */
    default boolean supportsLava() {
        return false;
    }

    /** Analysis outcome plus a findings map read by {@code SeedFilters}. */
    final class SeedAnalysis {
        private final boolean matches;
        private final Map<String, Object> findings;

        private SeedAnalysis(boolean matches, Map<String, Object> findings) {
            this.matches = matches;
            this.findings = findings == null
                    ? Collections.<String, Object>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(findings));
        }

        public static SeedAnalysis of(boolean matches, Map<String, Object> findings) {
            return new SeedAnalysis(matches, findings);
        }

        public static SeedAnalysis mismatch() {
            return new SeedAnalysis(false, null);
        }

        public boolean matches() {
            return matches;
        }

        public Map<String, Object> findings() {
            return findings;
        }
    }
}
