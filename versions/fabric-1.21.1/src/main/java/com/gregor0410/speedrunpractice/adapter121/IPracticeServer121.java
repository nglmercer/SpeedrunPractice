package com.gregor0410.speedrunpractice.adapter121;

import com.gregor0410.speedrunpractice.adapter121.world.PracticeWorld121;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import net.minecraft.server.world.ServerWorld;

import java.io.IOException;
import java.util.Map;

/**
 * Practice-world machinery on the 1.21.1 server mixin (mirrors the 1.16.1
 * {@code IMinecraftServer} and the 26.3 {@code IPracticeServer263} on yarn
 * mappings): linked overworld/nether/end triples plus single end worlds,
 * every level really generated from the requested practice seed.
 */
public interface IPracticeServer121 {
    Map<PracticeDimension, PracticeWorld121> createLinkedPracticeWorlds(long seed) throws IOException;
    PracticeWorld121 createEndPracticeWorld(long seed) throws IOException;
    /**
     * Removes one practice world at runtime (teleports its players back to
     * the main overworld spawn, unregisters it, clears its dragon bar, and
     * deletes its folder). A no-op for null, unknown, or already-removed
     * worlds.
     */
    void deletePracticeWorld(ServerWorld world);
    /** Deletes orphaned {@code speedrun_practice} dimension folders. */
    void pruneLeftoverPracticeWorlds();
}
