package com.gregor0410.speedrunpractice.common.seeds;

import com.gregor0410.speedrunpractice.common.util.SimpleJson;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local seed persistence (plan section 19):
 * {@code favorites.json}, {@code recent.json}, {@code failed.json},
 * {@code searches/*.json}, {@code imports/*.txt}.
 */
public final class SeedStore {
    private static final int RECENT_CAP = 50;
    private final Path dir;

    public SeedStore(Path dir) {
        if (dir == null) {
            throw new IllegalArgumentException("dir must not be null");
        }
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    private void ensureDirs() throws IOException {
        Files.createDirectories(dir);
        Files.createDirectories(dir.resolve("searches"));
        Files.createDirectories(dir.resolve("imports"));
        Files.createDirectories(dir.resolve("exports"));
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    // -- favorites (seed + tags + note) ------------------------------------

    /** One favorite entry. */
    public static final class FavoriteEntry {
        private final long seed;
        private final List<String> tags;
        private final String note;

        public FavoriteEntry(long seed, List<String> tags, String note) {
            this.seed = seed;
            this.tags = tags == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(tags));
            this.note = note;
        }

        public long seed() {
            return seed;
        }

        public List<String> tags() {
            return tags;
        }

        public String note() {
            return note;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("seed", seed);
            map.put("tags", tags);
            map.put("note", note);
            return map;
        }

        static FavoriteEntry fromMap(Map<String, Object> map) {
            long seed = ((Number) map.get("seed")).longValue();
            List<String> tags = new ArrayList<String>();
            Object rawTags = map.get("tags");
            if (rawTags instanceof List) {
                for (Object tag : (List<?>) rawTags) {
                    tags.add(String.valueOf(tag));
                }
            }
            Object note = map.get("note");
            return new FavoriteEntry(seed, tags, note == null ? null : String.valueOf(note));
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<FavoriteEntry> listFavorites() throws IOException {
        Path file = dir.resolve("favorites.json");
        if (!Files.exists(file)) {
            return new ArrayList<FavoriteEntry>();
        }
        try {
            Object parsed = SimpleJson.parse(read(file));
            List<FavoriteEntry> out = new ArrayList<FavoriteEntry>();
            if (parsed instanceof List) {
                for (Object entry : (List<?>) parsed) {
                    out.add(FavoriteEntry.fromMap((Map<String, Object>) entry));
                }
            }
            return out;
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Ignoring corrupt favorites.json: " + bad.getMessage());
            return new ArrayList<FavoriteEntry>();
        }
    }

    public List<Long> listFavoriteSeeds() throws IOException {
        List<Long> seeds = new ArrayList<Long>();
        for (FavoriteEntry entry : listFavorites()) {
            seeds.add(entry.seed());
        }
        return seeds;
    }

    public synchronized void addFavorite(long seed) throws IOException {
        List<FavoriteEntry> entries = listFavorites();
        for (FavoriteEntry entry : entries) {
            if (entry.seed() == seed) {
                return;
            }
        }
        entries.add(new FavoriteEntry(seed, null, null));
        saveFavorites(entries);
    }

    public synchronized void removeFavorite(long seed) throws IOException {
        List<FavoriteEntry> entries = listFavorites();
        List<FavoriteEntry> kept = new ArrayList<FavoriteEntry>();
        for (FavoriteEntry entry : entries) {
            if (entry.seed() != seed) {
                kept.add(entry);
            }
        }
        saveFavorites(kept);
    }

    public synchronized boolean isFavorite(long seed) throws IOException {
        for (FavoriteEntry entry : listFavorites()) {
            if (entry.seed() == seed) {
                return true;
            }
        }
        return false;
    }

    public synchronized void tagFavorite(long seed, String tag) throws IOException {
        updateFavorite(seed, tag, null, true);
    }

    public synchronized void noteFavorite(long seed, String note) throws IOException {
        updateFavorite(seed, null, note, false);
    }

    private void updateFavorite(long seed, String tag, String note, boolean isTag) throws IOException {
        List<FavoriteEntry> entries = listFavorites();
        List<FavoriteEntry> updated = new ArrayList<FavoriteEntry>();
        boolean found = false;
        for (FavoriteEntry entry : entries) {
            if (entry.seed() == seed) {
                found = true;
                List<String> tags = new ArrayList<String>(entry.tags());
                String keptNote = entry.note();
                if (isTag && tag != null && !tags.contains(tag)) {
                    tags.add(tag);
                }
                if (!isTag) {
                    keptNote = note;
                }
                updated.add(new FavoriteEntry(seed, tags, keptNote));
            } else {
                updated.add(entry);
            }
        }
        if (!found) {
            List<String> tags = new ArrayList<String>();
            if (isTag && tag != null) {
                tags.add(tag);
            }
            updated.add(new FavoriteEntry(seed, tags, isTag ? null : note));
        }
        saveFavorites(updated);
    }

    private void saveFavorites(List<FavoriteEntry> entries) throws IOException {
        ensureDirs();
        List<Object> rows = new ArrayList<Object>();
        for (FavoriteEntry entry : entries) {
            rows.add(entry.toMap());
        }
        write(dir.resolve("favorites.json"), SimpleJson.toJson(rows, true));
    }

    // -- recents --------------------------------------------------------------

    public synchronized List<Long> getRecent() throws IOException {
        Path file = dir.resolve("recent.json");
        if (!Files.exists(file)) {
            return new ArrayList<Long>();
        }
        try {
            Object parsed = SimpleJson.parse(read(file));
            List<Long> out = new ArrayList<Long>();
            if (parsed instanceof List) {
                for (Object seed : (List<?>) parsed) {
                    if (seed instanceof Number) {
                        out.add(((Number) seed).longValue());
                    }
                }
            }
            return out;
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Ignoring corrupt recent.json: " + bad.getMessage());
            return new ArrayList<Long>();
        }
    }

    public synchronized void addRecent(long seed) throws IOException {
        List<Long> recent = getRecent();
        recent.remove((Long) seed);
        recent.add(0, seed);
        while (recent.size() > RECENT_CAP) {
            recent.remove(recent.size() - 1);
        }
        ensureDirs();
        write(dir.resolve("recent.json"), SimpleJson.toJson(new ArrayList<Object>(recent), true));
    }

    // -- saved searches ---------------------------------------------------------

    public synchronized void saveSearch(String name, String json) throws IOException {
        ensureDirs();
        write(dir.resolve("searches").resolve(safeName(name) + ".json"), json);
    }

    public synchronized String loadSearch(String name) throws IOException {
        Path file = dir.resolve("searches").resolve(safeName(name) + ".json");
        return Files.exists(file) ? read(file) : null;
    }

    public synchronized List<String> listSearches() throws IOException {
        List<String> names = new ArrayList<String>();
        Path searches = dir.resolve("searches");
        if (!Files.isDirectory(searches)) {
            return names;
        }
        DirectoryStream<Path> stream = Files.newDirectoryStream(searches, "*.json");
        try {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                names.add(name.substring(0, name.length() - ".json".length()));
            }
        } finally {
            stream.close();
        }
        Collections.sort(names);
        return names;
    }

    // -- result exports -------------------------------------------------------------

    /**
     * Writes search results to {@code exports/<name>.json} (pretty JSON array
     * of {@link SeedResult} maps) and returns the file. Verification states
     * are stored as-is; nothing here ever marks a seed verified.
     */
    public synchronized Path exportResults(String name, List<SeedResult> results) throws IOException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("export name must not be empty");
        }
        ensureDirs();
        List<Object> rows = new ArrayList<Object>();
        if (results != null) {
            for (SeedResult result : results) {
                rows.add(result.toMap());
            }
        }
        Path file = dir.resolve("exports").resolve(safeName(name.trim()) + ".json");
        write(file, SimpleJson.toJson(rows, true));
        return file;
    }

    /** Names (without {@code .json}) of previous result exports. */
    public synchronized List<String> listExports() throws IOException {
        List<String> names = new ArrayList<String>();
        Path exports = dir.resolve("exports");
        if (!Files.isDirectory(exports)) {
            return names;
        }
        DirectoryStream<Path> stream = Files.newDirectoryStream(exports, "*.json");
        try {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                names.add(name.substring(0, name.length() - ".json".length()));
            }
        } finally {
            stream.close();
        }
        Collections.sort(names);
        return names;
    }

    // -- imports (one seed per line, '#' comments + blanks skipped) --------------

    public synchronized void writeImport(String name, List<String> lines) throws IOException {
        ensureDirs();
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        write(dir.resolve("imports").resolve(safeName(name) + ".txt"), out.toString());
    }

    public synchronized List<Long> readImport(String name) throws IOException {
        Path file = dir.resolve("imports").resolve(safeName(name) + ".txt");
        // Legacy flat file from the 1.16.1 SeedManager.
        Path legacy = dir.resolveSibling("speedrun-practice-seeds.txt");
        if (!Files.exists(file)) {
            file = legacy;
        }
        List<Long> seeds = new ArrayList<Long>();
        if (!Files.exists(file)) {
            return seeds;
        }
        int skipped = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            try {
                seeds.add(Long.parseLong(trimmed));
            } catch (NumberFormatException bad) {
                skipped++;
            }
        }
        if (skipped > 0) {
            SpeedrunLogger.warn("Skipped " + skipped + " invalid seed lines in " + file.getFileName());
        }
        return seeds;
    }

    // -- failed seeds ------------------------------------------------------------

    public synchronized void recordFailure(long seed) throws IOException {
        List<Long> failed = getFailed();
        if (!failed.contains(seed)) {
            failed.add(seed);
            ensureDirs();
            write(dir.resolve("failed.json"), SimpleJson.toJson(new ArrayList<Object>(failed), true));
        }
    }

    public synchronized List<Long> getFailed() throws IOException {
        Path file = dir.resolve("failed.json");
        if (!Files.exists(file)) {
            return new ArrayList<Long>();
        }
        try {
            Object parsed = SimpleJson.parse(read(file));
            List<Long> out = new ArrayList<Long>();
            if (parsed instanceof List) {
                for (Object seed : (List<?>) parsed) {
                    if (seed instanceof Number) {
                        out.add(((Number) seed).longValue());
                    }
                }
            }
            return out;
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Ignoring corrupt failed.json: " + bad.getMessage());
            return new ArrayList<Long>();
        }
    }

    public synchronized void clearFailures() throws IOException {
        ensureDirs();
        write(dir.resolve("failed.json"), "[]");
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }
}
