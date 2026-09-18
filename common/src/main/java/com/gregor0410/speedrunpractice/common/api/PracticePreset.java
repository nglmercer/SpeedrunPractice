package com.gregor0410.speedrunpractice.common.api;

/** Named starter shown on the scenario screen (e.g. "Housing Bastion Practice"). */
public final class PracticePreset {
    private final PracticeId id;
    private final String displayName;
    private final PracticeType type;
    private final PracticeSettings defaults;

    public PracticePreset(PracticeId id, String displayName, PracticeType type, PracticeSettings defaults) {
        if (id == null || displayName == null || type == null || defaults == null) {
            throw new IllegalArgumentException("preset fields must not be null");
        }
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.defaults = defaults;
    }

    public PracticeId id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public PracticeType type() {
        return type;
    }

    public PracticeSettings defaults() {
        return defaults;
    }
}
