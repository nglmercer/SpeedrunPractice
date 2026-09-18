package com.gregor0410.speedrunpractice.adapter121;

/**
 * Pure item-id normalization shared by the 1.21.1 registry and unit tests:
 * trim, lowercase, default to the {@code minecraft} namespace, and fall back
 * to {@code minecraft:air} for null input. Mirrors the 26.3 helper so the
 * Loom port reuses it unchanged.
 */
public final class RegistryIds {
    private RegistryIds() {
    }

    public static String normalizeItemId(String id) {
        if (id == null) {
            return "minecraft:air";
        }
        String trimmed = id.trim().toLowerCase();
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }
}
