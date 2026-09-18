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
}
