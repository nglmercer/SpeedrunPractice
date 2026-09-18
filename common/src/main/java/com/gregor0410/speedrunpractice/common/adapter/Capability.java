package com.gregor0410.speedrunpractice.common.adapter;

/**
 * Explicit per-version capability flags (plan section 28). Adapters report
 * support via {@code MinecraftAdapter.supports()}; GUI/commands disable or
 * explain unsupported options instead of crashing.
 */
public enum Capability {
    /** Creating extra practice dimensions/worlds at runtime. */
    CUSTOM_DIMENSION_RUNTIME,
    /** Recycled-world reset instead of full delete + recreate. */
    FAST_WORLD_RESET,
    /** Querying bastion subtype without generating chunks. */
    BASTION_TYPE_QUERY,
    /** Forcing the ender dragon into the perch phase. */
    DRAGON_FORCE_PERCH,
    /** Capturing/restoring portal linkage state. */
    PORTAL_STATE_CAPTURE,
    /** Reading structure metadata (portal rooms, eye counts) during search. */
    STRUCTURE_METADATA_SEARCH
}
