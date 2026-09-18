package com.gregor0410.speedrunpractice.seedsearch;

import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.util.SimpleJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded per-query memo of seed -> match verdicts (plan section 28),
 * with JSON export/import for warm restarts.
 */
public final class SearchCache {
    private final int perQueryCap;
    private final Map<String, LinkedHashMap<Long, Boolean>> cache = new HashMap<String, LinkedHashMap<Long, Boolean>>();

    public SearchCache() {
        this(100000);
    }

    public SearchCache(int perQueryCap) {
        if (perQueryCap < 1) {
            throw new IllegalArgumentException("cap must be >= 1");
        }
        this.perQueryCap = perQueryCap;
    }

    /** Stable key from the query's match-relevant fields. */
    public static String queryKey(SeedQuery query) {
        StringBuilder out = new StringBuilder(query.version().name()).append('|');
        out.append(query.requiredBiome()).append('|');
        List<String> structures = new ArrayList<String>(query.requiredStructures().keySet());
        Collections.sort(structures);
        for (String id : structures) {
            out.append(id).append('=').append(query.requiredStructures().get(id)).append(';');
        }
        out.append('|').append(query.maxDistance()).append('|');
        List<String> constraints = new ArrayList<String>(query.constraints().keySet());
        Collections.sort(constraints);
        for (String key : constraints) {
            out.append(key).append('=').append(query.constraints().get(key)).append(';');
        }
        return out.toString();
    }

    public synchronized Boolean get(SeedQuery query, long seed) {
        LinkedHashMap<Long, Boolean> verdicts = cache.get(queryKey(query));
        return verdicts == null ? null : verdicts.get(seed);
    }

    public synchronized void put(SeedQuery query, long seed, boolean match) {
        String key = queryKey(query);
        LinkedHashMap<Long, Boolean> verdicts = cache.get(key);
        if (verdicts == null) {
            verdicts = new LinkedHashMap<Long, Boolean>();
            cache.put(key, verdicts);
        }
        verdicts.put(seed, match);
        while (verdicts.size() > perQueryCap) {
            verdicts.remove(verdicts.keySet().iterator().next());
        }
    }

    public synchronized void clear(SeedQuery query) {
        cache.remove(queryKey(query));
    }

    public synchronized void clearAll() {
        cache.clear();
    }

    public synchronized int size(SeedQuery query) {
        LinkedHashMap<Long, Boolean> verdicts = cache.get(queryKey(query));
        return verdicts == null ? 0 : verdicts.size();
    }

    public synchronized String exportToJson() {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, LinkedHashMap<Long, Boolean>> entry : cache.entrySet()) {
            List<Object> matches = new ArrayList<Object>();
            List<Object> misses = new ArrayList<Object>();
            for (Map.Entry<Long, Boolean> verdict : entry.getValue().entrySet()) {
                if (Boolean.TRUE.equals(verdict.getValue())) {
                    matches.add(verdict.getKey());
                } else {
                    misses.add(verdict.getKey());
                }
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("matches", matches);
            row.put("misses", misses);
            root.put(entry.getKey(), row);
        }
        return SimpleJson.toJson(root, true);
    }

    @SuppressWarnings("unchecked")
    public synchronized void importFromJson(String json) {
        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Search cache JSON must be an object");
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) parsed).entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) entry.getValue();
            LinkedHashMap<Long, Boolean> verdicts = new LinkedHashMap<Long, Boolean>();
            addAll(verdicts, row.get("matches"), Boolean.TRUE);
            addAll(verdicts, row.get("misses"), Boolean.FALSE);
            while (verdicts.size() > perQueryCap) {
                verdicts.remove(verdicts.keySet().iterator().next());
            }
            cache.put(String.valueOf(entry.getKey()), verdicts);
        }
    }

    private static void addAll(LinkedHashMap<Long, Boolean> verdicts, Object raw, Boolean value) {
        if (!(raw instanceof List)) {
            return;
        }
        for (Object seed : (List<?>) raw) {
            if (seed instanceof Number) {
                verdicts.put(((Number) seed).longValue(), value);
            }
        }
    }
}
