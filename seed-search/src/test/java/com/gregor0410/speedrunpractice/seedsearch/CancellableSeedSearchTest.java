package com.gregor0410.speedrunpractice.seedsearch;

import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedFilters;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.seeds.TwoStageSeedSearch;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Worker-thread lifecycle: cancel releases the executor, shutdown is idempotent. */
public class CancellableSeedSearchTest {
    private static SeedAnalyzer matchAll() {
        return new SeedAnalyzer() {
            @Override
            public SeedAnalysis analyze(long seed, SeedQuery query) {
                return SeedAnalysis.of(true, null);
            }

            @Override
            public boolean matches(long seed, SeedQuery query) {
                return true;
            }
        };
    }

    private static CancellableSeedSearch search(long maxAttempts, int maxResults) {
        return new CancellableSeedSearch(SeedQuery.builder().build(), matchAll(),
                (TwoStageSeedSearch.Verifier) null, 0L, maxResults, maxAttempts,
                Collections.<SeedFilters.Filter>emptyList());
    }

    @Test
    public void cancelReleasesWorkerAndKeepsResults() throws Exception {
        CancellableSeedSearch search = search(1000L, 5);
        assertTrue(search.isShutdown());
        search.start();
        assertTrue(search.awaitCompletion(10000L));
        assertEquals(5, search.results().size());
        search.cancel();
        assertTrue(search.isShutdown());
        assertTrue(search.progress().cancelled());
        assertEquals(5, search.results().size());
    }

    @Test
    public void cancelBeforeStartIsHarmless() {
        CancellableSeedSearch search = search(100L, 10);
        search.cancel();
        assertTrue(search.isShutdown());
        assertTrue(search.results().isEmpty());
    }

    @Test
    public void shutdownIsIdempotent() throws Exception {
        CancellableSeedSearch search = search(100L, 2);
        search.start();
        assertTrue(search.awaitCompletion(10000L));
        search.shutdown();
        search.shutdown();
        assertTrue(search.isShutdown());
    }

    @Test
    public void doubleStartFails() throws Exception {
        CancellableSeedSearch search = search(100L, 2);
        search.start();
        try {
            search.start();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        } finally {
            search.shutdown();
        }
    }

    @Test
    public void startAfterCancelFailsWithoutLeaking() throws Exception {
        CancellableSeedSearch search = search(100L, 2);
        search.start();
        assertTrue(search.awaitCompletion(10000L));
        search.cancel();
        try {
            search.start();
            fail("expected IllegalStateException when restarting a cancelled search");
        } catch (IllegalStateException expected) {
        }
        assertTrue(search.isShutdown());
    }

    @Test
    public void failedResultsStayReadable() throws Exception {
        TwoStageSeedSearch.Verifier rejectAll = new TwoStageSeedSearch.Verifier() {
            @Override
            public boolean verify(long seed, com.gregor0410.speedrunpractice.common.seeds.SeedResult candidate) {
                return false;
            }
        };
        CancellableSeedSearch search = new CancellableSeedSearch(SeedQuery.builder().build(), matchAll(),
                rejectAll, 0L, 10, 5L, Collections.<SeedFilters.Filter>emptyList());
        search.start();
        assertTrue(search.awaitCompletion(10000L));
        assertTrue(search.results().isEmpty());
        assertEquals(5, search.failed().size());
        assertEquals(5L, search.progress().seedsFailed());
        search.shutdown();
    }

    @Test
    public void progressExposesCounters() throws Exception {
        CancellableSeedSearch search = search(50L, 3);
        search.start();
        assertTrue(search.awaitCompletion(10000L));
        assertEquals(3L, search.progress().seedsMatched());
        assertTrue(search.progress().seedsTested() >= 3L);
        assertTrue(search.isFinished());
        search.shutdown();
    }
}
