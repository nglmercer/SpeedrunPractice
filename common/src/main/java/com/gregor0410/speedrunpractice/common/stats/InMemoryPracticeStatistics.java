package com.gregor0410.speedrunpractice.common.stats;

import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.util.SimpleJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** In-memory statistics; a file-backed wrapper can delegate to this. */
public final class InMemoryPracticeStatistics implements PracticeStatistics {
    private final Map<String, List<AttemptRecord>> attempts = new HashMap<String, List<AttemptRecord>>();

    @Override
    public synchronized void recordAttempt(AttemptRecord attempt) {
        if (attempt == null) {
            throw new IllegalArgumentException("attempt must not be null");
        }
        List<AttemptRecord> list = attempts.get(attempt.practice().value());
        if (list == null) {
            list = new ArrayList<AttemptRecord>();
            attempts.put(attempt.practice().value(), list);
        }
        list.add(attempt);
    }

    @Override
    public synchronized int attempts(PracticeId practice) {
        return records(practice).size();
    }

    @Override
    public synchronized int completed(PracticeId practice) {
        int count = 0;
        for (AttemptRecord record : records(practice)) {
            if (record.completed()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized double completionRate(PracticeId practice) {
        List<AttemptRecord> list = records(practice);
        if (list.isEmpty()) {
            return 0.0;
        }
        return (double) completed(practice) / (double) list.size();
    }

    @Override
    public synchronized OptionalLong personalBest(PracticeId practice) {
        OptionalLong best = OptionalLong.empty();
        for (AttemptRecord record : records(practice)) {
            if (record.completed() && (!best.isPresent() || record.elapsedMs() < best.getAsLong())) {
                best = OptionalLong.of(record.elapsedMs());
            }
        }
        return best;
    }

    @Override
    public synchronized OptionalLong average(PracticeId practice) {
        long total = 0L;
        int count = 0;
        for (AttemptRecord record : records(practice)) {
            if (record.completed()) {
                total += record.elapsedMs();
                count++;
            }
        }
        return count == 0 ? OptionalLong.empty() : OptionalLong.of(total / count);
    }

    @Override
    public synchronized OptionalLong median(PracticeId practice) {
        List<Long> times = new ArrayList<Long>();
        for (AttemptRecord record : records(practice)) {
            if (record.completed()) {
                times.add(record.elapsedMs());
            }
        }
        if (times.isEmpty()) {
            return OptionalLong.empty();
        }
        Collections.sort(times);
        int middle = times.size() / 2;
        if (times.size() % 2 == 1) {
            return OptionalLong.of(times.get(middle));
        }
        return OptionalLong.of((times.get(middle - 1) + times.get(middle)) / 2L);
    }

    @Override
    public synchronized List<AttemptRecord> recent(PracticeId practice, int limit) {
        List<AttemptRecord> list = new ArrayList<AttemptRecord>(records(practice));
        Collections.reverse(list);
        if (limit >= 0 && list.size() > limit) {
            return Collections.unmodifiableList(list.subList(0, limit));
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public synchronized String exportJson() {
        List<Object> rows = new ArrayList<Object>();
        List<String> keys = new ArrayList<String>(attempts.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            for (AttemptRecord record : attempts.get(key)) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("practice", record.practice().value());
                row.put("version", record.version().versionString());
                row.put("seed", record.seed());
                row.put("elapsedMs", record.elapsedMs());
                row.put("completed", record.completed());
                row.put("timestampMs", record.timestampMs());
                row.put("resetReason", record.resetReason());
                row.put("preset", record.preset());
                rows.add(row);
            }
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("attempts", rows);
        return SimpleJson.toJson(root, true);
    }

    @Override
    public synchronized String exportCsv() {
        StringBuilder out = new StringBuilder("practice,version,seed,elapsedMs,completed,timestampMs,resetReason,preset\n");
        List<String> keys = new ArrayList<String>(attempts.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            List<AttemptRecord> list = new ArrayList<AttemptRecord>(attempts.get(key));
            Collections.sort(list, new Comparator<AttemptRecord>() {
                @Override
                public int compare(AttemptRecord left, AttemptRecord right) {
                    return Long.compare(left.timestampMs(), right.timestampMs());
                }
            });
            for (AttemptRecord record : list) {
                out.append(csv(record.practice().value())).append(',');
                out.append(csv(record.version().versionString())).append(',');
                out.append(record.seed()).append(',');
                out.append(record.elapsedMs()).append(',');
                out.append(record.completed()).append(',');
                out.append(record.timestampMs()).append(',');
                out.append(csv(record.resetReason())).append(',');
                out.append(csv(record.preset())).append('\n');
            }
        }
        return out.toString();
    }

    @Override
    public synchronized void reset(PracticeId practice) {
        attempts.remove(practice.value());
    }

    @Override
    public synchronized void resetAll() {
        attempts.clear();
    }

    private List<AttemptRecord> records(PracticeId practice) {
        List<AttemptRecord> list = attempts.get(practice.value());
        return list == null ? Collections.<AttemptRecord>emptyList() : list;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
