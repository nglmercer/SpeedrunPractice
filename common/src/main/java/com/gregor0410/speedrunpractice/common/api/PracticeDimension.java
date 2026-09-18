package com.gregor0410.speedrunpractice.common.api;

/** Minecraft dimension without touching Minecraft classes. */
public enum PracticeDimension {
    OVERWORLD("overworld"),
    NETHER("nether"),
    END("end");

    private final String id;

    PracticeDimension(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PracticeDimension fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("PracticeDimension id must not be null");
        }
        for (PracticeDimension dimension : values()) {
            if (dimension.id.equalsIgnoreCase(id.trim())) {
                return dimension;
            }
        }
        throw new IllegalArgumentException("Unknown dimension: " + id);
    }
}
