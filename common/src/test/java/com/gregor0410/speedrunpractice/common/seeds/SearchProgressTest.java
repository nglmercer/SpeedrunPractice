package com.gregor0410.speedrunpractice.common.seeds;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Extended search progress: verified/failed/elapsed/rate (plan section 48). */
public class SearchProgressTest {
    private static final Executor INLINE = new Executor() {
        @Override
        public void execute(Runnable task) {
            task.run();
        }
    };

    @Test
    public void legacyConstructorDefaultsNewCounters() {
        SeedSearchTask.SearchProgress progress = new SeedSearchTask.SearchProgress(10L, 3L, false, true);
        assertEquals(10L, progress.seedsTested());
        assertEquals(3L, progress.seedsMatched());
        assertEquals(0L, progress.seedsVerified());
        assertEquals(0L, progress.seedsFailed());
        assertEquals(0L, progress.elapsedMs());
        assertEquals(0.0, progress.ratePerSecond(), 0.0);
        assertTrue(progress.finished());
    }

    @Test
    public void rateReflectsElapsedTime() {
        SeedSearchTask.SearchProgress progress =
                new SeedSearchTask.SearchProgress(2000L, 2L, 2L, 0L, 1000L, false, true);
        assertEquals(2000.0, progress.ratePerSecond(), 1e-6);
    }

    @Test
    public void twoStageReportsVerifiedAndFailed() throws Exception {
        SeedAnalyzer matchAll = new SeedAnalyzer() {
            @Override
            public SeedAnalysis analyze(long seed, SeedQuery query) {
                return SeedAnalysis.of(true, null);
            }

            @Override
            public boolean matches(long seed, SeedQuery query) {
                return true;
            }
        };
        TwoStageSeedSearch.Verifier evenOnly = new TwoStageSeedSearch.Verifier() {
            @Override
            public boolean verify(long seed, SeedResult candidate) {
                return seed % 2 == 0;
            }
        };
        TwoStageSeedSearch search = new TwoStageSeedSearch(SeedQuery.builder().build(), matchAll, evenOnly,
                0L, 2, 100L, Collections.<SeedFilters.Filter>emptyList());
        search.start(INLINE);
        assertTrue(search.awaitCompletion(5000L));
        SeedSearchTask.SearchProgress progress = search.progress();
        assertEquals(2L, progress.seedsVerified());
        assertEquals(1L, progress.seedsFailed());
        assertTrue(progress.elapsedMs() >= 0L);
    }
}
