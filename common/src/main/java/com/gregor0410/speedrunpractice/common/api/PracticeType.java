package com.gregor0410.speedrunpractice.common.api;

/** The eleven practice categories (plan sections 12 and 44). */
public enum PracticeType {
    OVERWORLD("overworld", "Overworld"),
    BURIED_TREASURE("buried_treasure", "Buried Treasure"),
    NETHER("nether", "Nether"),
    BASTION("bastion", "Bastion"),
    FORTRESS("fortress", "Fortress"),
    BLIND_TRAVEL("blind_travel", "Blind Travel"),
    POSTBLIND("postblind", "Post Blind"),
    STRONGHOLD("stronghold", "Stronghold"),
    END("end", "End"),
    ONE_CYCLE("onecycle", "One Cycle"),
    CUSTOM("custom", "Custom");

    private final String id;
    private final String displayName;

    PracticeType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static PracticeType fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("PracticeType id must not be null");
        }
        for (PracticeType type : values()) {
            if (type.id.equalsIgnoreCase(id.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown practice type: " + id);
    }
}
