package com.gregor0410.speedrunpractice.common.seeds;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeedFiltersTest {
    private static SeedAnalyzer analyzerWith(final Map<String, Object> findings) {
        return new SeedAnalyzer() {
            @Override
            public SeedAnalysis analyze(long seed, SeedQuery query) {
                return SeedAnalysis.of(true, new HashMap<String, Object>(findings));
            }

            @Override
            public boolean matches(long seed, SeedQuery query) {
                return true;
            }
        };
    }

    private static Map<String, Object> findings() {
        Map<String, Object> findings = new HashMap<String, Object>();
        findings.put(SeedFilters.FIND_BIOME_SPAWN, "plains");
        findings.put("structure.fortress.distance", 300L);
        findings.put(SeedFilters.FIND_BASTION_TYPE, "housing");
        findings.put(SeedFilters.FIND_STRONGHOLD_RING, 2L);
        findings.put(SeedFilters.FIND_LAVA, Boolean.TRUE);
        return findings;
    }

    @Test
    public void biomeFilter() {
        SeedAnalyzer analyzer = analyzerWith(findings());
        SeedQuery query = SeedQuery.builder().build();
        assertTrue(new SeedFilters.BiomeFilter("plains").matches(1L, analyzer, query));
        assertFalse(new SeedFilters.BiomeFilter("desert").matches(1L, analyzer, query));
    }

    @Test
    public void structureDistanceFilter() {
        SeedAnalyzer analyzer = analyzerWith(findings());
        SeedQuery query = SeedQuery.builder().build();
        assertTrue(new SeedFilters.StructureDistanceFilter("fortress", 200, 500).matches(1L, analyzer, query));
        assertFalse(new SeedFilters.StructureDistanceFilter("fortress", 400, 500).matches(1L, analyzer, query));
        assertFalse(new SeedFilters.StructureDistanceFilter("village", 0, 500).matches(1L, analyzer, query));
    }

    @Test
    public void bastionRingAndLavaFilters() {
        SeedAnalyzer analyzer = analyzerWith(findings());
        SeedQuery query = SeedQuery.builder().build();
        assertTrue(new SeedFilters.BastionTypeFilter("housing").matches(1L, analyzer, query));
        assertFalse(new SeedFilters.BastionTypeFilter("bridge").matches(1L, analyzer, query));
        assertTrue(new SeedFilters.StrongholdRingFilter(2).matches(1L, analyzer, query));
        assertFalse(new SeedFilters.StrongholdRingFilter(3).matches(1L, analyzer, query));
        assertTrue(new SeedFilters.LavaFilter().matches(1L, analyzer, query));
    }

    @Test
    public void combinedAndAnyComposition() {
        SeedAnalyzer analyzer = analyzerWith(findings());
        SeedQuery query = SeedQuery.builder().build();
        SeedFilters.Filter good = new SeedFilters.BiomeFilter("plains");
        SeedFilters.Filter bad = new SeedFilters.BiomeFilter("desert");
        assertTrue(new SeedFilters.CombinedFilter(Arrays.asList(good, good)).matches(1L, analyzer, query));
        assertFalse(new SeedFilters.CombinedFilter(Arrays.asList(good, bad)).matches(1L, analyzer, query));
        assertTrue(new SeedFilters.AnyFilter(Arrays.asList(bad, good)).matches(1L, analyzer, query));
        assertFalse(new SeedFilters.AnyFilter(Arrays.asList(bad, bad)).matches(1L, analyzer, query));
    }
}
