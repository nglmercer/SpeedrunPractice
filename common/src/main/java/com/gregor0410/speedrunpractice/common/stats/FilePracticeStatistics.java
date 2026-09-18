package com.gregor0410.speedrunpractice.common.stats;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.util.SimpleJson;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * File-backed statistics (plan section 42). Delegates queries to an
 * in-memory store and persists {@code stats.json} after every mutation.
 * Writes go to a temporary file followed by an atomic replace, so a crash
 * can never corrupt the existing file; a corrupt file is backed up and
 * replaced by an empty store. Persistence failures warn but never break
 * gameplay. Attempts stay keyed by practice + version + preset (plan
 * section 43) via the fields on every record.
 */
public final class FilePracticeStatistics implements PracticeStatistics {
    private final InMemoryPracticeStatistics delegate = new InMemoryPracticeStatistics();
    private final Path file;

    public FilePracticeStatistics(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("stats file must not be null");
        }
        this.file = file;
        load();
    }

    public Path file() {
        return file;
    }

    @Override
    public synchronized void recordAttempt(AttemptRecord attempt) {
        delegate.recordAttempt(attempt);
        saveBestEffort();
    }

    @Override
    public synchronized int attempts(PracticeId practice) {
        return delegate.attempts(practice);
    }

    @Override
    public synchronized int completed(PracticeId practice) {
        return delegate.completed(practice);
    }

    @Override
    public synchronized double completionRate(PracticeId practice) {
        return delegate.completionRate(practice);
    }

    @Override
    public synchronized OptionalLong personalBest(PracticeId practice) {
        return delegate.personalBest(practice);
    }

    @Override
    public synchronized OptionalLong average(PracticeId practice) {
        return delegate.average(practice);
    }

    @Override
    public synchronized OptionalLong median(PracticeId practice) {
        return delegate.median(practice);
    }

    @Override
    public synchronized List<AttemptRecord> recent(PracticeId practice, int limit) {
        return delegate.recent(practice, limit);
    }

    @Override
    public synchronized String exportJson() {
        return delegate.exportJson();
    }

    @Override
    public synchronized String exportCsv() {
        return delegate.exportCsv();
    }

    @Override
    public synchronized void reset(PracticeId practice) {
        delegate.reset(practice);
        saveBestEffort();
    }

    @Override
    public synchronized void resetAll() {
        delegate.resetAll();
        saveBestEffort();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Map<String, Object> root = SimpleJson.parseObject(text);
            Object raw = root.get("attempts");
            if (!(raw instanceof List)) {
                return;
            }
            for (Object entry : (List<?>) raw) {
                if (!(entry instanceof Map)) {
                    continue;
                }
                Map<String, Object> row = (Map<String, Object>) entry;
                try {
                    delegate.recordAttempt(row(row));
                } catch (RuntimeException bad) {
                    SpeedrunLogger.warn("Skipping corrupt stats row: " + bad.getMessage());
                }
            }
        } catch (Exception failure) {
            backupCorrupt();
            SpeedrunLogger.error("Invalid stats file, backed up and reset: " + failure.getMessage());
        }
    }

    private static AttemptRecord row(Map<String, Object> row) {
        String practice = String.valueOf(row.get("practice"));
        GameVersion version = parseVersion(row.get("version"));
        long seed = asLong(row.get("seed"), 0L);
        long elapsed = asLong(row.get("elapsedMs"), 0L);
        boolean completed = Boolean.TRUE.equals(row.get("completed"));
        long timestamp = asLong(row.get("timestampMs"), 0L);
        Object reason = row.get("resetReason");
        Object preset = row.get("preset");
        return new AttemptRecord(PracticeId.of(practice), version, seed, elapsed, completed, timestamp,
                reason == null ? null : String.valueOf(reason),
                preset == null ? null : String.valueOf(preset));
    }

    private static GameVersion parseVersion(Object raw) {
        if (raw == null) {
            return GameVersion.MC_1_16_1;
        }
        try {
            return GameVersion.parse(String.valueOf(raw));
        } catch (IllegalArgumentException unknown) {
            return GameVersion.MC_1_16_1;
        }
    }

    private static long asLong(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void saveBestEffort() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, delegate.exportJson().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException fallback) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            SpeedrunLogger.warn("Cannot save statistics to " + file + ": " + failure.getMessage());
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Cannot save statistics to " + file + ": " + failure.getMessage());
        }
    }

    private void backupCorrupt() {
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".bak." + System.currentTimeMillis());
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            SpeedrunLogger.warn("Backed up corrupt stats to " + backup.getFileName());
        } catch (IOException backupFailure) {
            SpeedrunLogger.error("Could not back up corrupt stats", backupFailure);
        }
    }
}
