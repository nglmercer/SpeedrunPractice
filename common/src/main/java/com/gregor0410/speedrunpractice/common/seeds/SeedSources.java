package com.gregor0410.speedrunpractice.common.seeds;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;

/** The seven seed sources (plan section 15) sharing current/previous history. */
public final class SeedSources {
    private SeedSources() {
    }

    /** Resolves a scenario {@code seed.source} name to a live source. */
    public static SeedSource forName(String name, SeedStore store, SeedAnalyzer analyzer, SeedQuery query)
            throws PracticeException {
        String normalized = name == null ? "random" : name.trim().toLowerCase();
        try {
            if ("random".equals(normalized)) {
                return new RandomSeedSource();
            } else if ("fixed".equals(normalized)) {
                return new FixedSeedSource(fixedSeedFrom(query));
            } else if ("list".equals(normalized)) {
                return new SeedListSource(store == null ? Collections.<Long>emptyList() : store.readImport("seed-list"));
            } else if ("search".equals(normalized)) {
                if (analyzer == null || query == null) {
                    throw new PracticeException.SeedSearchException("Search source needs an analyzer and query",
                            "Seed search is not available here.");
                }
                return new SearchSeedSource(analyzer, query, 1000000L);
            } else if ("favorites".equals(normalized)) {
                return new FavoriteSeedSource(store == null ? Collections.<Long>emptyList() : store.listFavoriteSeeds());
            } else if ("recent".equals(normalized)) {
                return new RecentSeedSource(store == null ? Collections.<Long>emptyList() : store.getRecent());
            } else if ("imported".equals(normalized)) {
                String list = query == null ? null : query.constraints().get("seed.list");
                if (list == null) {
                    throw new PracticeException.SeedSearchException("Imported source needs seed.list",
                            "This scenario needs a seed list name (seed.list).");
                }
                return new ImportedSeedSource(list, store == null ? Collections.<Long>emptyList() : store.readImport(list));
            }
        } catch (java.io.IOException io) {
            throw new PracticeException.SeedSearchException("Cannot read seed store: " + io.getMessage(),
                    "Could not read saved seeds: " + io.getMessage());
        }
        throw new PracticeException.SeedSearchException("Unknown seed source: " + name,
                "Unknown seed source \"" + name + "\". Use random, fixed, list, search, favorites, recent or imported.");
    }

    private static long fixedSeedFrom(SeedQuery query) throws PracticeException {
        if (query == null || !query.constraints().containsKey("seed.value")) {
            throw new PracticeException.SeedSearchException("Fixed source needs seed.value",
                    "This scenario needs a fixed seed (seed.value).");
        }
        try {
            return Long.parseLong(query.constraints().get("seed.value").trim());
        } catch (NumberFormatException bad) {
            throw new PracticeException.SeedSearchException("Bad fixed seed: " + query.constraints().get("seed.value"),
                    "Fixed seed must be a number.");
        }
    }

    /** History: next appends (truncating redo), previous/current move a cursor. */
    public abstract static class BaseSeedSource implements SeedSource {
        private final List<Long> history = new ArrayList<Long>();
        private int position = -1;

        protected synchronized OptionalLong push(long seed) {
            while (history.size() > position + 1) {
                history.remove(history.size() - 1);
            }
            history.add(seed);
            position = history.size() - 1;
            return OptionalLong.of(seed);
        }

        @Override
        public synchronized OptionalLong previousSeed() {
            if (position > 0) {
                position--;
                return OptionalLong.of(history.get(position));
            }
            return OptionalLong.empty();
        }

        @Override
        public synchronized OptionalLong currentSeed() {
            if (position >= 0 && position < history.size()) {
                return OptionalLong.of(history.get(position));
            }
            return OptionalLong.empty();
        }
    }

    public static final class RandomSeedSource extends BaseSeedSource {
        private final Random random;

        public RandomSeedSource() {
            this.random = new Random();
        }

        public RandomSeedSource(long seed) {
            this.random = new Random(seed);
        }

        @Override
        public OptionalLong nextSeed(SeedRequest request) {
            return push(random.nextLong());
        }
    }

    public static final class FixedSeedSource extends BaseSeedSource {
        private final long seed;

        public FixedSeedSource(long seed) {
            this.seed = seed;
        }

        @Override
        public OptionalLong nextSeed(SeedRequest request) {
            return push(seed);
        }
    }

    /** Legacy-compatible round-robin list (replaces the old SeedManager cycling). */
    public static class SeedListSource extends BaseSeedSource {
        private List<Long> seeds;
        private int index;

        public SeedListSource(List<Long> seeds) {
            reload(seeds);
        }

        public synchronized void reload(List<Long> seeds) {
            this.seeds = seeds == null ? Collections.<Long>emptyList() : new ArrayList<Long>(seeds);
            this.index = 0;
        }

        public synchronized int getSeedCount() {
            return seeds.size();
        }

        @Override
        public synchronized OptionalLong nextSeed(SeedRequest request) {
            if (seeds.isEmpty()) {
                return OptionalLong.empty();
            }
            long seed = seeds.get(index);
            index = (index + 1) % seeds.size();
            return push(seed);
        }
    }

    /** Sequential scan through the analyzer until a query match (bounded attempts). */
    public static final class SearchSeedSource extends BaseSeedSource {
        private final SeedAnalyzer analyzer;
        private final SeedQuery query;
        private final long maxAttempts;
        private final Random random = new Random();

        public SearchSeedSource(SeedAnalyzer analyzer, SeedQuery query, long maxAttempts) {
            if (analyzer == null || query == null) {
                throw new IllegalArgumentException("analyzer and query must not be null");
            }
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            this.analyzer = analyzer;
            this.query = query;
            this.maxAttempts = maxAttempts;
        }

        @Override
        public OptionalLong nextSeed(SeedRequest request) {
            SeedQuery effective = request != null && request.query() != null ? request.query() : query;
            long start = random.nextLong();
            for (long i = 0; i < maxAttempts; i++) {
                long seed = start + i;
                if (analyzer.matches(seed, effective)) {
                    return push(seed);
                }
                SpeedrunLogger.debug("Seed " + seed + " rejected by search query");
            }
            return OptionalLong.empty();
        }
    }

    public static final class FavoriteSeedSource extends SeedListSource {
        public FavoriteSeedSource(List<Long> seeds) {
            super(seeds);
        }
    }

    public static final class RecentSeedSource extends SeedListSource {
        public RecentSeedSource(List<Long> seeds) {
            super(seeds);
        }
    }

    public static final class ImportedSeedSource extends SeedListSource {
        private final String name;

        public ImportedSeedSource(String name, List<Long> seeds) {
            super(seeds);
            this.name = name;
        }

        public String importName() {
            return name;
        }
    }
}
