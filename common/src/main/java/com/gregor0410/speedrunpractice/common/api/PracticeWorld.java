package com.gregor0410.speedrunpractice.common.api;

/**
 * Opaque handle to a version-specific practice world. Shared code only reads
 * metadata; all behaviour lives behind {@code WorldAdapter}.
 */
public interface PracticeWorld {
    /** Adapter-local handle id (never shown to users). */
    String handleId();

    long seed();

    PracticeDimension dimension();

    GameVersion version();
}
