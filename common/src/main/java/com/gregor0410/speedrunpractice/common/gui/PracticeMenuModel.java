package com.gregor0410.speedrunpractice.common.gui;

import com.gregor0410.speedrunpractice.common.adapter.Capability;
import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI content models (plan section 21). Pure data; each version's
 * {@code GuiAdapter} renders them with its own Screen API.
 */
public final class PracticeMenuModel {
    private PracticeMenuModel() {
    }

    /** One tile on the main menu. */
    public static final class MenuEntry {
        private final PracticeType type;
        private final String displayName;

        public MenuEntry(PracticeType type, String displayName) {
            this.type = type;
            this.displayName = displayName;
        }

        public PracticeType type() {
            return type;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** Main menu: Overworld, Nether, Bastion, Fortress, Blind Travel, Post Blind, Stronghold, End, One Cycle, Custom. */
    public static List<MenuEntry> defaultMenu() {
        List<MenuEntry> entries = new ArrayList<MenuEntry>();
        entries.add(new MenuEntry(PracticeType.OVERWORLD, "Overworld"));
        entries.add(new MenuEntry(PracticeType.NETHER, "Nether"));
        entries.add(new MenuEntry(PracticeType.BASTION, "Bastion"));
        entries.add(new MenuEntry(PracticeType.FORTRESS, "Fortress"));
        entries.add(new MenuEntry(PracticeType.BLIND_TRAVEL, "Blind Travel"));
        entries.add(new MenuEntry(PracticeType.POSTBLIND, "Post Blind"));
        entries.add(new MenuEntry(PracticeType.STRONGHOLD, "Stronghold"));
        entries.add(new MenuEntry(PracticeType.END, "End"));
        entries.add(new MenuEntry(PracticeType.ONE_CYCLE, "One Cycle"));
        entries.add(new MenuEntry(PracticeType.CUSTOM, "Custom"));
        return Collections.unmodifiableList(entries);
    }

    /** Scenario setup screen: preset + seed source + loadout + options + capability notes. */
    public static final class ScenarioScreen {
        private final PracticePreset preset;
        private final List<String> seedSourceOptions;
        private final List<String> loadoutOptions;
        private final Map<String, String> options;
        private final List<String> unsupportedNotes;

        private ScenarioScreen(PracticePreset preset, List<String> seedSourceOptions, List<String> loadoutOptions,
                               Map<String, String> options, List<String> unsupportedNotes) {
            this.preset = preset;
            this.seedSourceOptions = seedSourceOptions;
            this.loadoutOptions = loadoutOptions;
            this.options = options;
            this.unsupportedNotes = unsupportedNotes;
        }

        public static ScenarioScreen forPreset(PracticePreset preset, MinecraftAdapter adapter,
                                               List<String> loadoutIds) {
            List<String> notes = new ArrayList<String>();
            Map<String, String> options = new LinkedHashMap<String, String>(preset.defaults().asMap());
            if (preset.type() == PracticeType.BASTION && !adapter.supports(Capability.BASTION_TYPE_QUERY)) {
                notes.add("Bastion subtype detection is unavailable on " + adapter.version().versionString()
                        + "; the type filter is disabled.");
                options.remove("bastion.type");
            }
            if (preset.type() == PracticeType.ONE_CYCLE && !adapter.supports(Capability.DRAGON_FORCE_PERCH)) {
                notes.add("Forced perch is unavailable on " + adapter.version().versionString()
                        + "; the dragon must perch naturally.");
            }
            return new ScenarioScreen(preset,
                    Collections.unmodifiableList(Arrays.asList(
                            "random", "fixed", "list", "search", "favorites", "recent", "imported")),
                    Collections.unmodifiableList(new ArrayList<String>(loadoutIds)),
                    Collections.unmodifiableMap(options),
                    Collections.unmodifiableList(notes));
        }

        public PracticePreset preset() {
            return preset;
        }

        public List<String> seedSourceOptions() {
            return seedSourceOptions;
        }

        public List<String> loadoutOptions() {
            return loadoutOptions;
        }

        public Map<String, String> options() {
            return options;
        }

        public List<String> unsupportedNotes() {
            return unsupportedNotes;
        }
    }

    /** Post-attempt screen: time, PB and retry actions. */
    public static final class ResultsScreen {
        private final String displayName;
        private final long timeMs;
        private final Long personalBestMs;
        private final int attempts;
        private final List<String> actions;

        public ResultsScreen(String displayName, long timeMs, Long personalBestMs, int attempts) {
            this.displayName = displayName;
            this.timeMs = timeMs;
            this.personalBestMs = personalBestMs;
            this.attempts = attempts;
            this.actions = Collections.unmodifiableList(Arrays.asList(
                    "retry_same", "new_seed", "previous_seed", "save_seed", "main_menu"));
        }

        public String displayName() {
            return displayName;
        }

        public long timeMs() {
            return timeMs;
        }

        public Long personalBestMs() {
            return personalBestMs;
        }

        public int attempts() {
            return attempts;
        }

        public List<String> actions() {
            return actions;
        }

        /** "MM:SS.mmm" (hours fold into minutes). */
        public static String formatTime(long timeMs) {
            long total = Math.max(0L, timeMs);
            long minutes = total / 60000L;
            long seconds = (total % 60000L) / 1000L;
            long millis = total % 1000L;
            StringBuilder out = new StringBuilder();
            if (minutes < 10) {
                out.append('0');
            }
            out.append(minutes).append(':');
            if (seconds < 10) {
                out.append('0');
            }
            out.append(seconds).append('.');
            if (millis < 100) {
                out.append('0');
            }
            if (millis < 10) {
                out.append('0');
            }
            out.append(millis);
            return out.toString();
        }
    }

    /** Seed-search screen: preset, live counters and matched seeds. */
    public static final class SeedSearchScreen {
        private final String preset;
        private final long tested;
        private final long matched;
        private final long verified;
        private final long failed;
        private final boolean finished;
        private final boolean cancelled;
        private final List<Long> seeds;

        public SeedSearchScreen(String preset, long tested, long matched, long verified, long failed,
                                boolean finished, boolean cancelled, List<Long> seeds) {
            this.preset = preset;
            this.tested = Math.max(0L, tested);
            this.matched = Math.max(0L, matched);
            this.verified = Math.max(0L, verified);
            this.failed = Math.max(0L, failed);
            this.finished = finished;
            this.cancelled = cancelled;
            this.seeds = seeds == null
                    ? Collections.<Long>emptyList()
                    : Collections.unmodifiableList(new ArrayList<Long>(seeds));
        }

        public String preset() {
            return preset;
        }

        public long tested() {
            return tested;
        }

        public long matched() {
            return matched;
        }

        public long verified() {
            return verified;
        }

        public long failed() {
            return failed;
        }

        public boolean finished() {
            return finished;
        }

        public boolean cancelled() {
            return cancelled;
        }

        public List<Long> seeds() {
            return seeds;
        }
    }

    /** Loadout screen: preset list plus the selected preset. */
    public static final class LoadoutScreen {
        private final List<String> loadoutIds;
        private final String selectedId;

        public LoadoutScreen(List<String> loadoutIds, String selectedId) {
            this.loadoutIds = loadoutIds == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(loadoutIds));
            this.selectedId = selectedId;
        }

        public List<String> loadoutIds() {
            return loadoutIds;
        }

        /** Null when nothing is selected. */
        public String selectedId() {
            return selectedId;
        }
    }

    /** Statistics screen: one row per practice. */
    public static final class StatisticsScreen {
        /** One practice row. */
        public static final class Row {
            private final String displayName;
            private final int attempts;
            private final int completed;
            private final Long personalBestMs;

            public Row(String displayName, int attempts, int completed, Long personalBestMs) {
                this.displayName = displayName;
                this.attempts = attempts;
                this.completed = completed;
                this.personalBestMs = personalBestMs;
            }

            public String displayName() {
                return displayName;
            }

            public int attempts() {
                return attempts;
            }

            public int completed() {
                return completed;
            }

            public Long personalBestMs() {
                return personalBestMs;
            }
        }

        private final List<Row> rows;

        public StatisticsScreen(List<Row> rows) {
            this.rows = rows == null
                    ? Collections.<Row>emptyList()
                    : Collections.unmodifiableList(new ArrayList<Row>(rows));
        }

        public List<Row> rows() {
            return rows;
        }
    }

}
