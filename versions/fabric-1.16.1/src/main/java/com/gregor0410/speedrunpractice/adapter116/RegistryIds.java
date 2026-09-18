package com.gregor0410.speedrunpractice.adapter116;

/**
 * Pure item-id normalization shared by the live 1.16.1 registry and unit
 * tests: trim, lowercase, default to the {@code minecraft} namespace, and
 * fall back to {@code minecraft:air} for null input.
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
