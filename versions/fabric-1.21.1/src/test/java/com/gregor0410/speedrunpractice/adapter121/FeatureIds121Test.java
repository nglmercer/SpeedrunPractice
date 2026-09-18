package com.gregor0410.speedrunpractice.adapter121;

import com.gregor0410.speedrunpractice.adapter121.live.FeatureIds121;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Mapping-free structure-id vocabulary for 1.21.1: bare ids default to the
 * {@code minecraft} namespace, 1.16.1 aliases resolve to their modern
 * structure, and every id maps to its generating dimension. Mirrors the
 * offline-safe assertions of the 26.3 feature-id tests; the
 * {@code Identifier}/{@code ResourceKey} overloads are covered once the
 * Loom port lands.
 */
public class FeatureIds121Test {
    @Test
    public void bareStripsMinecraftNamespace() {
        assertEquals("village", FeatureIds121.bare("minecraft:village"));
        assertEquals("village", FeatureIds121.bare("village"));
        assertEquals("", FeatureIds121.bare(null));
    }

    @Test
    public void namespacedDefaultsToMinecraftNamespace() throws PracticeException {
        assertEquals("minecraft:village", FeatureIds121.namespaced("village"));
        assertEquals("minecraft:village", FeatureIds121.namespaced("minecraft:village"));
        assertEquals("minecraft:village_plains", FeatureIds121.namespaced(" Village_Plains "));
    }

    @Test
    public void oceanMonumentAliasesToMonument() throws PracticeException {
        assertEquals("minecraft:monument", FeatureIds121.namespaced("ocean_monument"));
        assertEquals("minecraft:monument", FeatureIds121.namespaced("minecraft:ocean_monument"));
    }

    @Test
    public void blankIdsFail() {
        try {
            FeatureIds121.namespaced("  ");
            fail("blank id must fail");
        } catch (PracticeException expected) {
        }
        try {
            FeatureIds121.namespaced(null);
            fail("null id must fail");
        } catch (PracticeException expected) {
        }
    }

    @Test
    public void homeDimensionsMatchWorldgen() {
        assertEquals(PracticeDimension.NETHER, FeatureIds121.homeDimension("bastion_remnant"));
        assertEquals(PracticeDimension.NETHER, FeatureIds121.homeDimension("minecraft:fortress"));
        assertEquals(PracticeDimension.NETHER, FeatureIds121.homeDimension("nether_fossil"));
        assertEquals(PracticeDimension.END, FeatureIds121.homeDimension("end_city"));
        assertEquals(PracticeDimension.OVERWORLD, FeatureIds121.homeDimension("village"));
        assertEquals(PracticeDimension.OVERWORLD, FeatureIds121.homeDimension("stronghold"));
        assertEquals(PracticeDimension.OVERWORLD, FeatureIds121.homeDimension("buried_treasure"));
    }

    @Test
    public void bastionAndStrongholdPredicates() {
        assertTrue(FeatureIds121.isBastion("bastion_remnant"));
        assertTrue(FeatureIds121.isBastion("minecraft:bastion_remnant"));
        assertFalse(FeatureIds121.isBastion("fortress"));
        assertTrue(FeatureIds121.isStronghold("stronghold"));
        assertTrue(FeatureIds121.isStronghold("Minecraft:Stronghold"));
        assertFalse(FeatureIds121.isStronghold("village"));
    }
}
