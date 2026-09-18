package com.gregor0410.speedrunpractice.common.seeds;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SeedSearchPresetTest {
    @Test
    public void fullPresetParses() throws Exception {
        SeedSearchPreset.ParsedPreset preset = SeedSearchPreset.parse("full", "{"
                + "\"version\": \"1.16.1\","
                + "\"biome\": \"minecraft:plains\","
                + "\"structures\": {\"minecraft:village\": 1500, \"minecraft:stronghold\": 2000},"
                + "\"bastionType\": \"treasure\","
                + "\"strongholdRing\": 1,"
                + "\"startSeed\": 7,"
                + "\"maxResults\": 3,"
                + "\"maxAttempts\": 500,"
                + "\"constraints\": {\"custom.key\": \"value\"}"
                + "}", GameVersion.MC_1_16_1);
        assertEquals("full", preset.name());
        assertEquals(GameVersion.MC_1_16_1, preset.query().version());
        assertEquals("minecraft:plains", preset.query().requiredBiome());
        assertEquals(Integer.valueOf(1500),
                preset.query().requiredStructures().get("minecraft:village"));
        assertEquals(Integer.valueOf(2000),
                preset.query().requiredStructures().get("minecraft:stronghold"));
        assertEquals("treasure", preset.query().bastionType());
        assertEquals(1, preset.query().strongholdRing());
        assertFalse(preset.query().lavaRequired());
        assertEquals("value", preset.query().constraints().get("custom.key"));
        assertEquals(Long.valueOf(7L), preset.startSeed());
        assertEquals(3, preset.maxResults());
        assertEquals(500L, preset.maxAttempts());
        // Two structure filters + bastion type + ring.
        assertEquals(4, preset.filters().size());
    }

    @Test
    public void defaultsApply() throws Exception {
        SeedSearchPreset.ParsedPreset preset =
                SeedSearchPreset.parse("empty", "{}", GameVersion.MC_1_21_1);
        assertEquals(GameVersion.MC_1_21_1, preset.query().version());
        assertNull(preset.query().requiredBiome());
        assertTrue(preset.query().requiredStructures().isEmpty());
        assertNull(preset.query().bastionType());
        assertEquals(0, preset.query().strongholdRing());
        assertFalse(preset.query().lavaRequired());
        assertNull(preset.startSeed());
        assertEquals(SeedSearchPreset.DEFAULT_MAX_RESULTS, preset.maxResults());
        assertEquals(SeedSearchPreset.DEFAULT_MAX_ATTEMPTS, preset.maxAttempts());
        assertTrue(preset.filters().isEmpty());
    }

    @Test
    public void lavaFlagSurvives() throws Exception {
        SeedSearchPreset.ParsedPreset preset =
                SeedSearchPreset.parse("lava", "{\"lava\": true}", GameVersion.MC_1_16_1);
        assertTrue(preset.query().lavaRequired());
    }

    @Test
    public void rejectsBadJson() {
        try {
            SeedSearchPreset.parse("bad", "{oops", GameVersion.MC_1_16_1);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("\"bad\""));
        }
    }

    @Test
    public void rejectsNonObject() {
        try {
            SeedSearchPreset.parse("bad", "[1, 2]", GameVersion.MC_1_16_1);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("JSON object"));
        }
    }

    @Test
    public void rejectsBadVersion() {
        try {
            SeedSearchPreset.parse("bad", "{\"version\": \"1.12\"}", GameVersion.MC_1_16_1);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("1.12"));
        }
    }

    @Test
    public void rejectsBadStructures() {
        try {
            SeedSearchPreset.parse("bad", "{\"structures\": [\"village\"]}", GameVersion.MC_1_16_1);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("structures"));
        }
    }

    @Test
    public void rejectsNegativeDistance() {
        try {
            SeedSearchPreset.parse("bad", "{\"structures\": {\"village\": -1}}", GameVersion.MC_1_16_1);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("village"));
        }
    }

    @Test
    public void rejectsBadBastionType() {
        try {
            SeedSearchPreset.parse("bad", "{\"bastionType\": \"gold\"}", GameVersion.MC_1_16_1);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("bastionType"));
        }
    }

    @Test
    public void rejectsBadRing() {
        try {
            SeedSearchPreset.parse("bad", "{\"strongholdRing\": 0}", GameVersion.MC_1_16_1);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("strongholdRing"));
        }
    }

    @Test
    public void rejectsBadBounds() {
        try {
            SeedSearchPreset.parse("bad", "{\"maxResults\": 0}", GameVersion.MC_1_16_1);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("maxResults"));
        }
        try {
            SeedSearchPreset.parse("bad", "{\"maxAttempts\": 0}", GameVersion.MC_1_16_1);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("maxAttempts"));
        }
    }

    @Test
    public void bastionVocabulary() {
        assertTrue(SeedSearchPreset.isBastionType("housing"));
        assertTrue(SeedSearchPreset.isBastionType("Stables"));
        assertTrue(SeedSearchPreset.isBastionType("treasure"));
        assertTrue(SeedSearchPreset.isBastionType("bridge"));
        assertFalse(SeedSearchPreset.isBastionType("gold"));
        assertFalse(SeedSearchPreset.isBastionType(null));
    }
}
