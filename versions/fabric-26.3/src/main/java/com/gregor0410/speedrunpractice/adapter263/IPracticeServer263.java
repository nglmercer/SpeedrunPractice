package com.gregor0410.speedrunpractice.adapter263;

import com.gregor0410.speedrunpractice.adapter263.world.PracticeLevel263;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.Map;

/**
 * Server-side practice-world lifecycle, implemented by the 26.3 server
 * mixin. Mirrors the 1.16.1 {@code IMinecraftServer} contract on modern
 * mappings: linked overworld/nether/end triples for general practices,
 * single end worlds for end practices.
 */
public interface IPracticeServer263 {
    /** Creates one linked triple for {@code seed}, pruning the previous set. */
    Map<PracticeDimension, PracticeLevel263> createLinkedPracticeWorlds(long seed) throws IOException;

    /** Creates a single end practice world for {@code seed}. */
    PracticeLevel263 createEndPracticeWorld(long seed) throws IOException;

    /**
     * Removes one practice level at runtime (teleports its players back to
     * the main overworld spawn, unregisters it, clears its dragon bar, and
     * deletes its folder). A no-op for null, unknown, or vanilla levels.
     */
    void deletePracticeLevel(ServerLevel level);

    /** Deletes leftover practice folders from sessions that never cleaned up. */
    void pruneLeftoverPracticeLevels();
}
