package com.gregor0410.speedrunpractice.common.stats;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeId;

import java.util.List;
import java.util.OptionalLong;

/**
 * Local attempt statistics (plan section 25). No accounts, no cloud; JSON and
 * CSV export for spreadsheets.
 */
public interface PracticeStatistics {
    void recordAttempt(AttemptRecord attempt);

    int attempts(PracticeId practice);

    int completed(PracticeId practice);

    /** 0.0 when there are no attempts. */
    double completionRate(PracticeId practice);

    OptionalLong personalBest(PracticeId practice);

    OptionalLong average(PracticeId practice);

    OptionalLong median(PracticeId practice);

    List<AttemptRecord> recent(PracticeId practice, int limit);

    String exportJson();

    String exportCsv();

    void reset(PracticeId practice);

    void resetAll();

    /** One recorded attempt (completed or not). */
    final class AttemptRecord {
        private final PracticeId practice;
        private final GameVersion version;
        private final long seed;
        private final long elapsedMs;
        private final boolean completed;
        private final long timestampMs;
        private final String resetReason;
        private final String preset;

        public AttemptRecord(PracticeId practice, GameVersion version, long seed, long elapsedMs,
                             boolean completed, long timestampMs, String resetReason, String preset) {
            if (practice == null || version == null) {
                throw new IllegalArgumentException("practice and version must not be null");
            }
            this.practice = practice;
            this.version = version;
            this.seed = seed;
            this.elapsedMs = Math.max(0L, elapsedMs);
            this.completed = completed;
            this.timestampMs = timestampMs;
            this.resetReason = resetReason;
            this.preset = preset;
        }

        public PracticeId practice() {
            return practice;
        }

        public GameVersion version() {
            return version;
        }

        public long seed() {
            return seed;
        }

        public long elapsedMs() {
            return elapsedMs;
        }

        public boolean completed() {
            return completed;
        }

        public long timestampMs() {
            return timestampMs;
        }

        public String resetReason() {
            return resetReason;
        }

        public String preset() {
            return preset;
        }
    }
}
