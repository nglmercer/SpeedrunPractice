package com.gregor0410.speedrunpractice.common.adapter;

/**
 * Version-aware id translation. Shared code speaks plain namespaced ids
 * ({@code minecraft:...}); each version normalises and validates them here.
 */
public interface RegistryAdapter {
    /** Normalises an id ("iron_pickaxe" -> "minecraft:iron_pickaxe"). */
    String normalizeItemId(String id);

    /** False for ids that do not exist on this version (preset import skips them). */
    boolean itemExists(String id);

    /** Stack size used to clamp loadout counts. */
    int maxStackSize(String id);
}
