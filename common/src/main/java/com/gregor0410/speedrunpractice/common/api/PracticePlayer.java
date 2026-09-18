package com.gregor0410.speedrunpractice.common.api;

/**
 * Opaque handle to the practicing player. Shared code never sees
 * {@code ServerPlayerEntity}; behaviour lives behind {@code PlayerAdapter}.
 */
public interface PracticePlayer {
    /** Adapter-local handle id (never shown to users). */
    String handleId();
}
