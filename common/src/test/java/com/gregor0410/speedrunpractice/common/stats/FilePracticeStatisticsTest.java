package com.gregor0410.speedrunpractice.common.stats;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Persistent statistics: atomic writes, lenient loads, version slices. */
public class FilePracticeStatisticsTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static PracticeStatistics.AttemptRecord record(String practice, GameVersion version,
                                                           long seed, long elapsedMs, boolean completed,
                                                           String preset) {
        return new PracticeStatistics.AttemptRecord(PracticeId.of(practice), version, seed, elapsedMs,
                completed, 1700000000000L, null, preset);
    }

    @Test
    public void roundTripAcrossInstances() throws Exception {
        Path file = folder.getRoot().toPath().resolve("stats.json");
        FilePracticeStatistics stats = new FilePracticeStatistics(file);
        stats.recordAttempt(record("end", GameVersion.MC_1_16_1, 42L, 9000L, true, "end_default"));
        stats.recordAttempt(record("end", GameVersion.MC_1_16_1, 43L, 8000L, false, "end_default"));
        assertTrue(Files.exists(file));

        FilePracticeStatistics reloaded = new FilePracticeStatistics(file);
        assertEquals(2, reloaded.attempts(PracticeId.of("end")));
        assertEquals(1, reloaded.completed(PracticeId.of("end")));
        assertEquals(9000L, reloaded.personalBest(PracticeId.of("end")).getAsLong());
    }

    @Test
    public void slicesNeverMixVersionsOrPresets() throws Exception {
        Path file = folder.getRoot().toPath().resolve("stats.json");
        FilePracticeStatistics stats = new FilePracticeStatistics(file);
        stats.recordAttempt(record("bastion", GameVersion.MC_1_16_1, 1L, 5000L, true, "housing"));
        stats.recordAttempt(record("bastion", GameVersion.MC_1_21_1, 2L, 3000L, true, "housing"));

        assertEquals(1, stats.attempts(PracticeId.of("bastion"), GameVersion.MC_1_16_1, "housing"));
        assertEquals(3000L, stats.personalBest(PracticeId.of("bastion"), GameVersion.MC_1_21_1, "housing")
                .getAsLong());
        assertEquals(2, stats.attempts(PracticeId.of("bastion"), null, null));
        assertEquals(0, stats.attempts(PracticeId.of("bastion"), GameVersion.MC_1_16_1, "bridge"));
        assertFalse(stats.personalBest(PracticeId.of("bastion"), GameVersion.V_26_3, null).isPresent());
    }

    @Test
    public void corruptFileBacksUpAndResets() throws Exception {
        Path file = folder.getRoot().toPath().resolve("stats.json");
        Files.write(file, "{not json".getBytes(StandardCharsets.UTF_8));
        FilePracticeStatistics stats = new FilePracticeStatistics(file);
        assertEquals(0, stats.attempts(PracticeId.of("end")));
        assertTrue(Files.list(file.getParent()).anyMatch(p -> p.getFileName().toString()
                .startsWith("stats.json.bak.")));
    }

    @Test
    public void unknownVersionFallsBackWithoutLosingRows() throws Exception {
        Path file = folder.getRoot().toPath().resolve("stats.json");
        Files.write(file, ("{\"attempts\": [{\"practice\": \"end\", \"version\": \"9.9.9\","
                + " \"seed\": 7, \"elapsedMs\": 100, \"completed\": true,"
                + " \"timestampMs\": 1, \"resetReason\": null, \"preset\": null}]}")
                .getBytes(StandardCharsets.UTF_8));
        FilePracticeStatistics stats = new FilePracticeStatistics(file);
        assertEquals(1, stats.attempts(PracticeId.of("end")));
        assertEquals(1, stats.attempts(PracticeId.of("end"), GameVersion.MC_1_16_1, null));
    }

    @Test
    public void missingFileStartsEmpty() throws Exception {
        Path file = folder.getRoot().toPath().resolve("nested").resolve("stats.json");
        FilePracticeStatistics stats = new FilePracticeStatistics(file);
        assertEquals(0, stats.attempts(PracticeId.of("end")));
        stats.recordAttempt(record("end", GameVersion.MC_1_16_1, 1L, 5L, true, null));
        assertTrue(Files.exists(file));
    }

    @Test
    public void resetPersists() throws Exception {
        Path file = folder.getRoot().toPath().resolve("stats.json");
        FilePracticeStatistics stats = new FilePracticeStatistics(file);
        stats.recordAttempt(record("end", GameVersion.MC_1_16_1, 1L, 5L, true, null));
        stats.reset(PracticeId.of("end"));
        assertEquals(0, new FilePracticeStatistics(file).attempts(PracticeId.of("end")));
    }
}
