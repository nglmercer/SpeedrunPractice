package com.gregor0410.speedrunpractice.common.seeds;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SeedQueryTest {
    @Test
    public void defaultsMatchAnything() {
        SeedQuery query = SeedQuery.builder().build();
        assertEquals(GameVersion.MC_1_16_1, query.version());
        assertNull(query.requiredBiome());
        assertTrue(query.requiredStructures().isEmpty());
        assertEquals(Integer.MAX_VALUE, query.maxDistance());
        assertTrue(query.constraints().isEmpty());
        assertNull(query.bastionType());
        assertEquals(0, query.strongholdRing());
        assertFalse(query.lavaRequired());
    }

    @Test
    public void subtypeRingAndLava() {
        SeedQuery query = SeedQuery.builder()
                .bastionType("Treasure")
                .strongholdRing(2)
                .requireLava()
                .build();
        assertEquals("treasure", query.bastionType());
        assertEquals(2, query.strongholdRing());
        assertTrue(query.lavaRequired());
    }

    @Test
    public void builderExampleFromPlan() {
        SeedQuery query = SeedQuery.builder()
                .version(GameVersion.MC_1_16_1)
                .requireBiome("minecraft:plains")
                .requireStructure("fortress", 500)
                .maxDistance(10000)
                .build();
        assertEquals(GameVersion.MC_1_16_1, query.version());
        assertEquals("minecraft:plains", query.requiredBiome());
        assertEquals(Integer.valueOf(500), query.requiredStructures().get("fortress"));
        assertEquals(10000, query.maxDistance());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyStructureId() {
        SeedQuery.builder().requireStructure("  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeMaxDistance() {
        SeedQuery.builder().maxDistance(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullVersion() {
        SeedQuery.builder().version(null);
    }
}
