package com.gregor0410.speedrunpractice.common.loadout;

import com.gregor0410.speedrunpractice.common.adapter.RegistryAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cross-version loadout filtering (plan section 66). Item ids are
 * normalised through the version registry; unknown ids are skipped with a
 * warning and counts are clamped to the version's max stack size, so one
 * missing item degrades a preset instead of crashing the practice.
 */
public final class LoadoutCompatibility {
    private LoadoutCompatibility() {
    }

    /** Filtered preset plus one warning per skipped/clamped stack. */
    public static final class Result {
        private final Loadout loadout;
        private final List<String> warnings;

        private Result(Loadout loadout, List<String> warnings) {
            this.loadout = loadout;
            this.warnings = Collections.unmodifiableList(warnings);
        }

        public Loadout loadout() {
            return loadout;
        }

        public List<String> warnings() {
            return warnings;
        }

        /** True when at least one stack was skipped or clamped. */
        public boolean degraded() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Filters {@code loadout} for the given version registry. Never returns
     * null and never throws for unknown items; those become warnings.
     */
    public static Result filter(RegistryAdapter registries, Loadout loadout) {
        if (registries == null || loadout == null) {
            throw new IllegalArgumentException("registries and loadout must not be null");
        }
        List<Loadout.Item> kept = new ArrayList<Loadout.Item>();
        List<String> warnings = new ArrayList<String>();
        for (Loadout.Item item : loadout.items()) {
            String normalized = registries.normalizeItemId(item.itemId());
            if (normalized == null || normalized.trim().isEmpty() || !registries.itemExists(normalized)) {
                warnings.add("Skipped unknown item \"" + item.itemId() + "\" in loadout \"" + loadout.id()
                        + "\" (not on this version).");
                continue;
            }
            int max = registries.maxStackSize(normalized);
            if (max < 1) {
                max = 64;
            }
            int count = item.count();
            if (count > max) {
                warnings.add("Clamped \"" + normalized + "\" to " + max + " in loadout \"" + loadout.id()
                        + "\" (max stack on this version).");
                count = max;
            }
            if (count < 1) {
                warnings.add("Clamped \"" + normalized + "\" to 1 in loadout \"" + loadout.id() + "\".");
                count = 1;
            }
            kept.add(new Loadout.Item(normalized.trim(), item.slot(), count));
        }
        return new Result(new Loadout(loadout.id(), kept), warnings);
    }
}
