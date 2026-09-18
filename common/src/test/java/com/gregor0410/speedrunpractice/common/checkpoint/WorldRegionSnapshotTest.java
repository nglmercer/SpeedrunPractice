package com.gregor0410.speedrunpractice.common.checkpoint;

import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/** Bounded region snapshot model (plan section 41). */
public class WorldRegionSnapshotTest {
    @Test
    public void storesAndReadsBlocks() {
        Map<String, String> blocks = new HashMap<String, String>();
        blocks.put(WorldRegionSnapshot.key(1, 64, 2), "minecraft:obsidian");
        WorldRegionSnapshot snapshot = new WorldRegionSnapshot(
                new PracticePosition(0.0, 64.0, 0.0), 4, blocks);
        assertEquals("minecraft:obsidian", snapshot.blockAt(1, 64, 2));
        assertNull(snapshot.blockAt(9, 64, 9));
        assertEquals(1, snapshot.size());
        assertEquals(4, snapshot.radius());
    }

    @Test
    public void nullBlocksBecomeEmpty() {
        WorldRegionSnapshot snapshot = new WorldRegionSnapshot(new PracticePosition(0.0, 0.0, 0.0), 0, null);
        assertEquals(0, snapshot.size());
        assertNull(snapshot.blockAt(0, 0, 0));
    }

    @Test
    public void rejectsBadBounds() {
        try {
            new WorldRegionSnapshot(null, 1, null);
            fail("expected IllegalArgumentException for null center");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new WorldRegionSnapshot(new PracticePosition(0.0, 0.0, 0.0), -1, null);
            fail("expected IllegalArgumentException for negative radius");
        } catch (IllegalArgumentException expected) {
        }
    }
}
