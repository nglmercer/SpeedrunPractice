package com.gregor0410.speedrunpractice.common.api;

/** Stable string identity for a practice scenario (e.g. "bastion_housing_default"). */
public final class PracticeId {
    private final String value;

    private PracticeId(String value) {
        this.value = value;
    }

    public static PracticeId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("PracticeId must be a non-empty string");
        }
        return new PracticeId(value.trim());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PracticeId)) {
            return false;
        }
        return value.equals(((PracticeId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
