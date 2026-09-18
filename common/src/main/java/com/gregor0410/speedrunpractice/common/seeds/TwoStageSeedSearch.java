package com.gregor0410.speedrunpractice.common.seeds;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Two-stage search (plan section 18). Stage A rejects cheaply via
 * {@link SeedAnalyzer} + filters; stage B verifies survivors through a
 * version-side {@link Verifier} (real chunk generation). Runs on the given
 * executor so the game thread never blocks; cancellable at any time.
 */
public final class TwoStageSeedSearch implements SeedSearchTask {
    /** Version-side chunk-generation check for stage-B survivors. */
    public interface Verifier {
        boolean verify(long seed, SeedResult candidate) throws PracticeException;
    }

    private final SeedQuery query;
    private final SeedAnalyzer analyzer;
    private final Verifier verifier;
    private final long startSeed;
    private final int maxResults;
    private final long maxAttempts;
    private final List<SeedFilters.Filter> filters;
    private final Map<Long, SeedAnalyzer.SeedAnalysis> analysisCache =
            Collections.synchronizedMap(new LinkedHashMap<Long, SeedAnalyzer.SeedAnalysis>());
    private final List<SeedResult> results = Collections.synchronizedList(new ArrayList<SeedResult>());
    private final List<SeedResult> failed = Collections.synchronizedList(new ArrayList<SeedResult>());
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile long seedsTested;
    private volatile boolean cancelled;
    private volatile boolean started;
    private volatile boolean finished;

    public TwoStageSeedSearch(SeedQuery query, SeedAnalyzer analyzer, Verifier verifier,
                              long startSeed, int maxResults, long maxAttempts,
                              List<SeedFilters.Filter> filters) {
        if (query == null || analyzer == null) {
            throw new IllegalArgumentException("query and analyzer must not be null");
        }
        if (maxResults < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("maxResults and maxAttempts must be >= 1");
        }
        this.query = query;
        this.analyzer = analyzer;
        this.verifier = verifier;
        this.startSeed = startSeed;
        this.maxResults = maxResults;
        this.maxAttempts = maxAttempts;
        this.filters = filters == null
                ? Collections.<SeedFilters.Filter>emptyList()
                : Collections.unmodifiableList(new ArrayList<SeedFilters.Filter>(filters));
    }

    /** Starts the scan on the given executor (call once). */
    public synchronized void start(Executor executor) {
        if (started) {
            throw new IllegalStateException("search already started");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        started = true;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        });
    }

    private void loop() {
        try {
            for (long i = 0; i < maxAttempts && results.size() < maxResults && !cancelled; i++) {
                long seed = startSeed + i;
                seedsTested++;
                if (!analyzer.matches(seed, query)) {
                    continue;
                }
                List<String> matched = new ArrayList<String>();
                boolean ok = true;
                for (SeedFilters.Filter filter : filters) {
                    if (filter.matches(seed, analyzer, query)) {
                        matched.add(filter.id());
                    } else {
                        SpeedrunLogger.debug("Seed " + seed + " rejected by filter " + filter.id());
                        ok = false;
                        break;
                    }
                }
                if (!ok) {
                    continue;
                }
                SeedAnalyzer.SeedAnalysis analysis = analysisCache.get(seed);
                if (analysis == null) {
                    analysis = analyzer.analyze(seed, query);
                    analysisCache.put(seed, analysis);
                }
                SeedResult candidate = new SeedResult(seed, query.version(), matched,
                        extractLocations(analysis), SeedResult.VerificationState.UNVERIFIED,
                        System.currentTimeMillis());
                if (verifier == null) {
                    results.add(candidate);
                } else {
                    try {
                        if (verifier.verify(seed, candidate)) {
                            results.add(candidate.withVerificationState(SeedResult.VerificationState.VERIFIED));
                        } else {
                            failed.add(candidate.withVerificationState(SeedResult.VerificationState.FAILED));
                        }
                    } catch (PracticeException failure) {
                        SpeedrunLogger.warn("Verification failed for seed " + seed + ": " + failure.getMessage());
                        failed.add(candidate.withVerificationState(SeedResult.VerificationState.FAILED));
                    }
                }
            }
        } finally {
            finished = true;
            done.countDown();
        }
    }

    private Map<String, StructureAdapter.StructureLocation> extractLocations(SeedAnalyzer.SeedAnalysis analysis) {
        Map<String, StructureAdapter.StructureLocation> locations =
                new LinkedHashMap<String, StructureAdapter.StructureLocation>();
        for (Map.Entry<String, Object> entry : analysis.findings().entrySet()) {
            if (entry.getKey().startsWith("location.") && entry.getValue() instanceof PracticePosition) {
                String id = entry.getKey().substring("location.".length());
                locations.put(id, new StructureAdapter.StructureLocation(id, (PracticePosition) entry.getValue(), null));
            }
        }
        return locations;
    }

    @Override
    public SearchProgress progress() {
        return new SearchProgress(seedsTested, results.size(), cancelled, finished);
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public List<SeedResult> results() {
        synchronized (results) {
            return Collections.unmodifiableList(new ArrayList<SeedResult>(results));
        }
    }

    /** Stage-B rejects (for the failed-seeds store). */
    public List<SeedResult> failed() {
        synchronized (failed) {
            return Collections.unmodifiableList(new ArrayList<SeedResult>(failed));
        }
    }

    public boolean awaitCompletion(long timeoutMs) throws InterruptedException {
        return done.await(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
