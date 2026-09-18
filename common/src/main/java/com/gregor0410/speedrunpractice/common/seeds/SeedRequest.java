package com.gregor0410.speedrunpractice.common.seeds;

/** Context for one {@code nextSeed} call: optional query override plus salt. */
public final class SeedRequest {
    private final SeedQuery query;
    private final long salt;

    private SeedRequest(SeedQuery query, long salt) {
        this.query = query;
        this.salt = salt;
    }

    public static SeedRequest any() {
        return new SeedRequest(null, System.nanoTime());
    }

    public static SeedRequest withQuery(SeedQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        return new SeedRequest(query, System.nanoTime());
    }

    /** Null means "use the source's own query". */
    public SeedQuery query() {
        return query;
    }

    public long salt() {
        return salt;
    }
}
