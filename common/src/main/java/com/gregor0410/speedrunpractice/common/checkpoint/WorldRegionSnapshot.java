package com.gregor0410.speedrunpractice.common.checkpoint;

import com.gregor0410.speedrunpractice.common.api.PracticePosition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded local block snapshot (plan section 41). Covers one small region
 * (portal frames, beds, obsidian, dragon-fight blocks) for checkpoint
 * restore; never the whole world. This class is the shared data model only —
 * capture/restore against live chunks is version-adapter work and pending,
 * so checkpoints currently restore world, player, scenario and timer state.
 */
public final class WorldRegionSnapshot {
    private final PracticePosition center;
    private final int radius;
    private final Map<String, String> blocks;

    /**
     * @param center region center; must not be null
     * @param radius horizontal/vertical half-extent in blocks; must be >= 0
     * @param blocks {@code "x,y,z" -> block id} entries inside the region
     */
    public WorldRegionSnapshot(PracticePosition center, int radius, Map<String, String> blocks) {
        if (center == null) {
            throw new IllegalArgumentException("center must not be null");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0");
        }
        this.center = center;
        this.radius = radius;
        this.blocks = blocks == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(blocks));
    }

    /** Key format for {@link #blocks()}. */
    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public PracticePosition center() {
        return center;
    }

    public int radius() {
        return radius;
    }

    public Map<String, String> blocks() {
        return blocks;
    }

    /** Null when the block was not captured. */
    public String blockAt(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    public int size() {
        return blocks.size();
    }
}
