package com.gregor0410.speedrunpractice.common.seeds;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeedSourcesTest {
    private static SeedAnalyzer evenAnalyzer() {
        return new SeedAnalyzer() {
            @Override
            public SeedAnalysis analyze(long seed, SeedQuery query) {
                return SeedAnalysis.of(seed % 2 == 0, null);
            }

            @Override
            public boolean matches(long seed, SeedQuery query) {
                return seed % 2 == 0;
            }
        };
    }

    @Test
    public void fixedSourceAlwaysReturnsItsSeed() {
        SeedSources.FixedSeedSource source = new SeedSources.FixedSeedSource(7L);
        assertEquals(7L, source.nextSeed(SeedRequest.any()).getAsLong());
        assertEquals(7L, source.nextSeed(SeedRequest.any()).getAsLong());
        assertEquals(7L, source.currentSeed().getAsLong());
    }

    @Test
    public void seededRandomSourcesAgree() {
        SeedSources.RandomSeedSource left = new SeedSources.RandomSeedSource(42L);
        SeedSources.RandomSeedSource right = new SeedSources.RandomSeedSource(42L);
        assertEquals(left.nextSeed(SeedRequest.any()).getAsLong(), right.nextSeed(SeedRequest.any()).getAsLong());
    }

    @Test
    public void listSourceRoundRobinsWithHistory() {
        SeedSources.SeedListSource source = new SeedSources.SeedListSource(Arrays.asList(1L, 2L));
        assertEquals(2, source.getSeedCount());
        assertEquals(1L, source.nextSeed(SeedRequest.any()).getAsLong());
        assertEquals(2L, source.nextSeed(SeedRequest.any()).getAsLong());
        assertEquals(1L, source.nextSeed(SeedRequest.any()).getAsLong());
        // History cursor: [1,2,1] -> previous is 2, then 1; then exhausted.
        assertEquals(2L, source.previousSeed().getAsLong());
        assertEquals(2L, source.currentSeed().getAsLong());
        assertEquals(1L, source.previousSeed().getAsLong());
        assertFalse(source.previousSeed().isPresent());
    }

    @Test
    public void emptyListYieldsNothing() {
        SeedSources.SeedListSource source = new SeedSources.SeedListSource(Collections.<Long>emptyList());
        assertFalse(source.nextSeed(SeedRequest.any()).isPresent());
        assertFalse(source.currentSeed().isPresent());
        assertFalse(source.previousSeed().isPresent());
    }

    @Test
    public void searchSourceFindsMatchingSeed() {
        SeedSources.SearchSeedSource source =
                new SeedSources.SearchSeedSource(evenAnalyzer(), SeedQuery.builder().build(), 10000L);
        OptionalLong found = source.nextSeed(SeedRequest.any());
        assertTrue(found.isPresent());
        assertEquals(0L, found.getAsLong() % 2L);
    }

    @Test
    public void searchSourceGivesUpAfterMaxAttempts() {
        SeedAnalyzer never = new SeedAnalyzer() {
            @Override
            public SeedAnalysis analyze(long seed, SeedQuery query) {
                return SeedAnalysis.mismatch();
            }

            @Override
            public boolean matches(long seed, SeedQuery query) {
                return false;
            }
        };
        SeedSources.SearchSeedSource source = new SeedSources.SearchSeedSource(never, SeedQuery.builder().build(), 50L);
        assertFalse(source.nextSeed(SeedRequest.any()).isPresent());
    }
}
