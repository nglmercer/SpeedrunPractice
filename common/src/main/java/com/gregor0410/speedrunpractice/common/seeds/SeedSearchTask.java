package com.gregor0410.speedrunpractice.common.seeds;

import java.util.List;

/** Cancellable search handle (plan section 38). Never locks the game. */
public interface SeedSearchTask {
    SearchProgress progress();

    boolean isFinished();

    void cancel();

    List<SeedResult> results();

    final class SearchProgress {
        private final long seedsTested;
        private final long seedsMatched;
        private final boolean cancelled;
        private final boolean finished;

        public SearchProgress(long seedsTested, long seedsMatched, boolean cancelled, boolean finished) {
            this.seedsTested = seedsTested;
            this.seedsMatched = seedsMatched;
            this.cancelled = cancelled;
            this.finished = finished;
        }

        public long seedsTested() {
            return seedsTested;
        }

        public long seedsMatched() {
            return seedsMatched;
        }

        public boolean cancelled() {
            return cancelled;
        }

        public boolean finished() {
            return finished;
        }
    }
}
