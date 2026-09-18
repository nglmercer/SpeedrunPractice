package com.gregor0410.speedrunpractice.common.seeds;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Analyze-once search architecture (plan section 45): each seed is analyzed
 * exactly once no matter how many filters run, and legacy single-filter
 * calls still analyze exactly once.
 */
public class SeedFiltersAnalyzeOnceTest {
    private static final Executor INLINE = new Executor() {
        @Override
        public void execute(Runnable task) {
            task.run();
        }
    };

    private static final class CountingAnalyzer implements SeedAnalyzer {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public SeedAnalysis analyze(long seed, SeedQuery query) {
            calls.incrementAndGet();
            Map<String, Object> findings = new HashMap<String, Object>();
            findings.put(SeedFilters.FIND_BIOME_SPAWN, "plains");
            findings.put("structure.fortress.distance", 300L);
            return SeedAnalysis.of(true, findings);
        }

        @Override
        public boolean matches(long seed, SeedQuery query) {
            return true;
        }
    }

    @Test
    public void twoStageSearchAnalyzesEachSeedOnce() throws Exception {
        CountingAnalyzer analyzer = new CountingAnalyzer();
        List<SeedFilters.Filter> filters = Arrays.<SeedFilters.Filter>asList(
                new SeedFilters.BiomeFilter("plains"),
                new SeedFilters.StructureDistanceFilter("fortress", 200, 500),
                new SeedFilters.LavaFilter());
        TwoStageSeedSearch search = new TwoStageSeedSearch(SeedQuery.builder().build(), analyzer, null,
                0L, 10, 5L, filters);
        search.start(INLINE);
        assertTrue(search.awaitCompletion(5000L));
        // 5 seeds x 3 filters, but exactly one analysis per seed.
        assertEquals(5, analyzer.calls.get());
        // Lava is absent from the findings, so nothing matches.
        assertTrue(search.results().isEmpty());
        assertEquals(5L, search.progress().seedsTested());
        assertEquals(0L, search.progress().seedsMatched());
    }

    @Test
    public void legacyFilterCallAnalyzesOnce() {
        CountingAnalyzer analyzer = new CountingAnalyzer();
        SeedQuery query = SeedQuery.builder().build();
        SeedFilters.Filter combined = new SeedFilters.CombinedFilter(Arrays.<SeedFilters.Filter>asList(
                new SeedFilters.BiomeFilter("plains"),
                new SeedFilters.StructureDistanceFilter("fortress", 200, 500)));
        assertTrue(combined.matches(1L, analyzer, query));
        assertEquals(1, analyzer.calls.get());
    }

    @Test
    public void analysisOverloadNeedsNoAnalyzer() {
        CountingAnalyzer analyzer = new CountingAnalyzer();
        SeedQuery query = SeedQuery.builder().build();
        SeedAnalyzer.SeedAnalysis analysis = analyzer.analyze(9L, query);
        assertEquals(1, analyzer.calls.get());
        SeedFilters.Filter filter = new SeedFilters.BiomeFilter("plains");
        assertTrue(filter.matches(9L, analysis, query));
        assertFalse(new SeedFilters.BiomeFilter("desert").matches(9L, analysis, query));
        assertEquals(1, analyzer.calls.get());
    }

    @Test
    public void legacyCustomFilterSurvivesAnalyzeOncePath() throws Exception {
        CountingAnalyzer analyzer = new CountingAnalyzer();
        SeedFilters.Filter custom = new SeedFilters.Filter() {
            @Override
            public String id() {
                return "custom";
            }

            @Override
            public boolean matches(long seed, SeedAnalyzer replay, SeedQuery query) {
                // Legacy shape: asks the analyzer, which replays the cached analysis.
                return "plains".equals(SeedFilters.findingString(
                        replay.analyze(seed, query).findings(), SeedFilters.FIND_BIOME_SPAWN));
            }
        };
        TwoStageSeedSearch search = new TwoStageSeedSearch(SeedQuery.builder().build(), analyzer, null,
                0L, 2, 3L, Collections.singletonList(custom));
        search.start(INLINE);
        assertTrue(search.awaitCompletion(5000L));
        assertEquals(2, search.results().size());
        assertEquals(2, analyzer.calls.get());
    }
}
