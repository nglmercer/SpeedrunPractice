package com.gregor0410.speedrunpractice.adapter263;

import com.gregor0410.speedrunpractice.adapter263.live.FeatureIds263;
import net.minecraft.core.registries.Registries;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Structure-id vocabulary for 26.3: bare ids default to the
 * {@code minecraft} namespace, registry/tag keys share the parsed
 * identifier, and 1.16.1 aliases resolve to their modern structure.
 */
public class FeatureIds263Test {
    @Test
    public void bareStripsMinecraftNamespace() {
        assertEquals("village", FeatureIds263.bare("minecraft:village"));
        assertEquals("village", FeatureIds263.bare("village"));
        assertEquals("", FeatureIds263.bare(null));
    }

    @Test
    public void identifierDefaultsToMinecraftNamespace() throws Exception {
        assertEquals("minecraft:village", FeatureIds263.identifier("village").toString());
        assertEquals("minecraft:village", FeatureIds263.identifier("minecraft:village").toString());
        assertEquals("minecraft:village_plains",
                FeatureIds263.identifier(" Village_Plains ").toString());
    }

    @Test
    public void oceanMonumentAliasesToMonument() throws Exception {
        assertEquals("minecraft:monument", FeatureIds263.identifier("ocean_monument").toString());
        assertEquals("minecraft:monument",
                FeatureIds263.identifier("minecraft:ocean_monument").toString());
        assertEquals("minecraft:monument", FeatureIds263.key("ocean_monument").identifier().toString());
    }

    @Test
    public void keysShareRegistryAndIdentifier() throws Exception {
        assertEquals(Registries.STRUCTURE, FeatureIds263.key("village").registryKey());
        assertEquals("minecraft:village", FeatureIds263.key("village").identifier().toString());
        assertEquals(Registries.STRUCTURE, FeatureIds263.tagKey("village").registry());
        assertEquals("minecraft:village", FeatureIds263.tagKey("village").location().toString());
    }

    @Test
    public void blankAndMalformedIdsFail() {
        try {
            FeatureIds263.identifier("  ");
            fail("blank id must fail");
        } catch (Exception expected) {
        }
        try {
            FeatureIds263.identifier("minecraft:not a structure!");
            fail("malformed id must fail");
        } catch (Exception expected) {
        }
    }
}
