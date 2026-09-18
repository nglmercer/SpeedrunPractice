package com.gregor0410.speedrunpractice.common.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * String-map settings for one scenario run. Well-known keys:
 * {@code seed.source}, {@code loadout}, plus per-practice keys documented in
 * {@code docs/practices.md}. Unknown keys are ignored, never fatal.
 */
public final class PracticeSettings {
    private final Map<String, String> options = new LinkedHashMap<String, String>();

    public PracticeSettings() {
    }

    public PracticeSettings(Map<String, String> options) {
        if (options != null) {
            this.options.putAll(options);
        }
    }

    public String get(String key) {
        return options.get(key);
    }

    public String getOrDefault(String key, String fallback) {
        String value = options.get(key);
        return value == null ? fallback : value;
    }

    public int getInt(String key, int fallback) {
        String value = options.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException bad) {
            return fallback;
        }
    }

    public double getDouble(String key, double fallback) {
        String value = options.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException bad) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = options.get(key);
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public void set(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("setting key must not be null");
        }
        if (value == null) {
            options.remove(key);
        } else {
            options.put(key, value);
        }
    }

    public String seedSource() {
        return getOrDefault("seed.source", "random");
    }

    public String loadoutId() {
        return get("loadout");
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(options));
    }
}
