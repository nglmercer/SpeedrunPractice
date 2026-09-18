package com.gregor0410.speedrunpractice.common.api;

/** Exactly the three supported Minecraft targets (plan section 1). */
public enum GameVersion {
    MC_1_16_1("1.16.1"),
    MC_1_21_1("1.21.1"),
    V_26_3("26.3");

    private final String versionString;

    GameVersion(String versionString) {
        this.versionString = versionString;
    }

    public String versionString() {
        return versionString;
    }

    /** Parses "1.16.1", "1.21.1" or "26.3" (also accepts "MC_1_16_1" style names). */
    public static GameVersion parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("GameVersion must not be null");
        }
        String normalized = text.trim();
        for (GameVersion version : values()) {
            if (version.versionString.equals(normalized) || version.name().equalsIgnoreCase(normalized)) {
                return version;
            }
        }
        throw new IllegalArgumentException("Unsupported game version: " + text);
    }
}
