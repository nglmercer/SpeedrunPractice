package com.gregor0410.speedrunpractice.common.seeds;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TwoStageSeedSearchTest {
    private static final Executor INLINE = new Executor() {
        @Override
        public void execute(Runnable task) {
            task.run();
        }
    };

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

    @Test
    public void verifiesSurvivorsAndRecordsFailures() throws Exception {
        TwoStageSeedSearch.Verifier evenOnly = new TwoStageSeedSearch.Verifier() {
            @Override
            public boolean verify(long seed, SeedResult candidate) {
                return seed % 2 == 0;
            }
        };
        TwoStageSeedSearch search = new TwoStageSeedSearch(SeedQuery.builder().build(), matchAll(), evenOnly,
                0L, 2, 100L, Collections.<SeedFilters.Filter>emptyList());
        search.start(INLINE);
        assertTrue(search.awaitCompletion(5000L));
        assertTrue(search.isFinished());
        List<SeedResult> results = search.results();
        assertEquals(2, results.size());
        assertEquals(0L, results.get(0).seed());
        assertEquals(2L, results.get(1).seed());
        for (SeedResult result : results) {
            assertEquals(SeedResult.VerificationState.VERIFIED, result.verificationState());
        }
        // Seeds 0 (verified), 1 (failed), 2 (verified): the scan stops once
        // maxResults is reached, so seed 3 is never tested.
        assertEquals(1, search.failed().size());
        assertEquals(3L, search.progress().seedsTested());
        assertEquals(2L, search.progress().seedsMatched());
    }

    @Test
    public void withoutVerifierResultsStayUnverified() throws Exception {
        TwoStageSeedSearch search = new TwoStageSeedSearch(SeedQuery.builder().build(), matchAll(), null,
                10L, 1, 10L, Collections.<SeedFilters.Filter>emptyList());
        search.start(INLINE);
        assertTrue(search.awaitCompletion(5000L));
        assertEquals(1, search.results().size());
        assertEquals(SeedResult.VerificationState.UNVERIFIED, search.results().get(0).verificationState());
    }

    @Test
    public void cancelledSearchTestsNothing() throws Exception {
        TwoStageSeedSearch search = new TwoStageSeedSearch(SeedQuery.builder().build(), matchAll(), null,
                0L, 10, 1000L, Collections.<SeedFilters.Filter>emptyList());
        search.cancel();
        search.start(INLINE);
        assertTrue(search.awaitCompletion(5000L));
        assertEquals(0L, search.progress().seedsTested());
        assertTrue(search.results().isEmpty());
        assertTrue(search.progress().cancelled());
    }

    @Test
    public void verifierExceptionsBecomeFailures() throws Exception {
        TwoStageSeedSearch.Verifier broken = new TwoStageSeedSearch.Verifier() {
            @Override
            public boolean verify(long seed, SeedResult candidate) throws PracticeException {
                throw new PracticeException("boom");
            }
        };
        TwoStageSeedSearch search = new TwoStageSeedSearch(SeedQuery.builder().build(), matchAll(), broken,
                0L, 10, 5L, Collections.<SeedFilters.Filter>emptyList());
        search.start(INLINE);
        assertTrue(search.awaitCompletion(5000L));
        assertTrue(search.results().isEmpty());
        assertEquals(5, search.failed().size());
    }
}
