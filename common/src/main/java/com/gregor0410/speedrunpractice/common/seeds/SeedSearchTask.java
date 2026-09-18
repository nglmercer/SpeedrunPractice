package com.gregor0410.speedrunpractice.common.seeds;

import java.util.List;

/** Cancellable search handle (plan section 38). Never locks the game. */
public interface SeedSearchTask {
    SearchProgress progress();

    boolean isFinished();

    void cancel();

    List<SeedResult> results();

    /** Live search counters (plan section 48). */
    final class SearchProgress {
        private final long seedsTested;
        private final long seedsMatched;
        private final long seedsVerified;
        private final long seedsFailed;
        private final long elapsedMs;
        private final boolean cancelled;
        private final boolean finished;

        public SearchProgress(long seedsTested, long seedsMatched, boolean cancelled, boolean finished) {
            this(seedsTested, seedsMatched, 0L, 0L, 0L, cancelled, finished);
        }

        public SearchProgress(long seedsTested, long seedsMatched, long seedsVerified, long seedsFailed,
                              long elapsedMs, boolean cancelled, boolean finished) {
            this.seedsTested = Math.max(0L, seedsTested);
            this.seedsMatched = Math.max(0L, seedsMatched);
            this.seedsVerified = Math.max(0L, seedsVerified);
            this.seedsFailed = Math.max(0L, seedsFailed);
            this.elapsedMs = Math.max(0L, elapsedMs);
            this.cancelled = cancelled;
            this.finished = finished;
        }

        public long seedsTested() {
            return seedsTested;
        }

        public long seedsMatched() {
            return seedsMatched;
        }

        /** Stage-B successes (0 when the search has no verifier). */
        public long seedsVerified() {
            return seedsVerified;
        }

        /** Stage-B rejects. */
        public long seedsFailed() {
            return seedsFailed;
        }

        /** Milliseconds since the search started. */
        public long elapsedMs() {
            return elapsedMs;
        }

        /** Tested seeds per second, or 0 when nothing measurable elapsed. */
        public double ratePerSecond() {
            if (elapsedMs <= 0L || seedsTested <= 0L) {
                return 0.0;
            }
            return (double) seedsTested * 1000.0 / (double) elapsedMs;
        }

        public boolean cancelled() {
            return cancelled;
        }

        public boolean finished() {
            return finished;
        }
    }
}
