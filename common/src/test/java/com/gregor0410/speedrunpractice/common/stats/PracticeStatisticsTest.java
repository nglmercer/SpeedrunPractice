package com.gregor0410.speedrunpractice.common.stats;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PracticeStatisticsTest {
    private static final PracticeId BASTION = PracticeId.of("bastion");
    private static final PracticeId END = PracticeId.of("end");

    private static PracticeStatistics.AttemptRecord attempt(PracticeId practice, long elapsed, boolean completed,
                                                            long timestamp) {
        return new PracticeStatistics.AttemptRecord(practice, GameVersion.MC_1_16_1, 42L, elapsed, completed,
                timestamp, completed ? null : "SAME_SEED", null);
    }

    @Test
    public void aggregatesAttempts() {
        InMemoryPracticeStatistics stats = new InMemoryPracticeStatistics();
        stats.recordAttempt(attempt(BASTION, 100L, true, 1L));
        stats.recordAttempt(attempt(BASTION, 300L, true, 2L));
        stats.recordAttempt(attempt(BASTION, 50L, false, 3L));
        assertEquals(3, stats.attempts(BASTION));
        assertEquals(2, stats.completed(BASTION));
        assertEquals(2.0 / 3.0, stats.completionRate(BASTION), 0.0001);
        assertEquals(100L, stats.personalBest(BASTION).getAsLong());
        assertEquals(200L, stats.average(BASTION).getAsLong());
        assertEquals(200L, stats.median(BASTION).getAsLong());
        assertEquals(2, stats.recent(BASTION, 2).size());
        assertEquals(3L, stats.recent(BASTION, 5).get(0).timestampMs());
    }

    @Test
    public void evenMedianAveragesMiddleTwo() {
        InMemoryPracticeStatistics stats = new InMemoryPracticeStatistics();
        stats.recordAttempt(attempt(END, 100L, true, 1L));
        stats.recordAttempt(attempt(END, 200L, true, 2L));
        stats.recordAttempt(attempt(END, 300L, true, 3L));
        stats.recordAttempt(attempt(END, 400L, true, 4L));
        assertEquals(250L, stats.median(END).getAsLong());
    }

    @Test
    public void emptyPracticeHasNoRecords() {
        InMemoryPracticeStatistics stats = new InMemoryPracticeStatistics();
        assertEquals(0, stats.attempts(END));
        assertEquals(0.0, stats.completionRate(END), 0.0);
        assertFalse(stats.personalBest(END).isPresent());
        assertFalse(stats.average(END).isPresent());
        assertFalse(stats.median(END).isPresent());
        assertTrue(stats.recent(END, 5).isEmpty());
    }

    @Test
    public void exportsAndResets() {
        InMemoryPracticeStatistics stats = new InMemoryPracticeStatistics();
        stats.recordAttempt(attempt(BASTION, 100L, true, 1L));
        assertTrue(stats.exportJson().contains("bastion"));
        assertTrue(stats.exportCsv().startsWith("practice,version,seed,elapsedMs,completed,timestampMs,resetReason,preset\n"));
        assertTrue(stats.exportCsv().contains("bastion,1.16.1,42,100,true,1,,"));
        stats.reset(BASTION);
        assertEquals(0, stats.attempts(BASTION));
        stats.recordAttempt(attempt(END, 100L, true, 1L));
        stats.resetAll();
        assertEquals(0, stats.attempts(END));
    }
}
