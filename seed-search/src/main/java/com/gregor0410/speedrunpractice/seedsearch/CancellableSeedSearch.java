package com.gregor0410.speedrunpractice.seedsearch;

import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedFilters;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.seeds.SeedResult;
import com.gregor0410.speedrunpractice.common.seeds.SeedSearchTask;
import com.gregor0410.speedrunpractice.common.seeds.TwoStageSeedSearch;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Owns the background thread for a {@link TwoStageSeedSearch}: start, cancel,
 * await, shut down. Game code never manages search threads directly.
 */
public final class CancellableSeedSearch implements SeedSearchTask {
    private final TwoStageSeedSearch delegate;
    private ExecutorService executor;

    public CancellableSeedSearch(SeedQuery query, SeedAnalyzer analyzer,
                                 TwoStageSeedSearch.Verifier verifier, long startSeed,
                                 int maxResults, long maxAttempts, List<SeedFilters.Filter> filters) {
        this.delegate = new TwoStageSeedSearch(query, analyzer, verifier, startSeed, maxResults, maxAttempts, filters);
    }

    /** Starts the worker thread (call once). */
    public synchronized void start() {
        if (executor != null) {
            throw new IllegalStateException("search already started");
        }
        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "speedrun-practice-seed-search");
                thread.setDaemon(true);
                return thread;
            }
        });
        delegate.start(executor);
    }

    @Override
    public SearchProgress progress() {
        return delegate.progress();
    }

    @Override
    public boolean isFinished() {
        return delegate.isFinished();
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }

    @Override
    public List<SeedResult> results() {
        return delegate.results();
    }

    public List<SeedResult> failed() {
        return delegate.failed();
    }

    public boolean awaitCompletion(long timeoutMs) throws InterruptedException {
        return delegate.awaitCompletion(timeoutMs);
    }

    /** Cancels and releases the worker thread. */
    public synchronized void shutdown() {
        delegate.cancel();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
