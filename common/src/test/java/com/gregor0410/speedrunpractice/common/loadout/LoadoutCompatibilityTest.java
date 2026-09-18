package com.gregor0410.speedrunpractice.common.loadout;

import com.gregor0410.speedrunpractice.common.adapter.RegistryAdapter;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Cross-version loadout filtering: skip unknown, clamp stacks, warn. */
public class LoadoutCompatibilityTest {
    private static RegistryAdapter registries() {
        return new RegistryAdapter() {
            @Override
            public String normalizeItemId(String id) {
                if (id == null) {
                    return null;
                }
                return id.contains(":") ? id : "minecraft:" + id;
            }

            @Override
            public boolean itemExists(String id) {
                return !"minecraft:removed_item".equals(id);
            }

            @Override
            public int maxStackSize(String id) {
                return "minecraft:ender_pearl".equals(id) ? 16 : 64;
            }
        };
    }

    @Test
    public void validLoadoutPassesThrough() {
        Loadout loadout = new Loadout("kit", Arrays.asList(
                new Loadout.Item("minecraft:stone", 0, 64),
                new Loadout.Item("dirt", 1, 3)));
        LoadoutCompatibility.Result result = LoadoutCompatibility.filter(registries(), loadout);
        assertFalse(result.degraded());
        assertTrue(result.warnings().isEmpty());
        assertEquals(2, result.loadout().items().size());
        assertEquals("minecraft:dirt", result.loadout().items().get(1).itemId());
    }

    @Test
    public void unknownItemsSkippedWithWarning() {
        Loadout loadout = new Loadout("kit", Arrays.asList(
                new Loadout.Item("minecraft:stone", 0, 1),
                new Loadout.Item("minecraft:removed_item", 1, 1)));
        LoadoutCompatibility.Result result = LoadoutCompatibility.filter(registries(), loadout);
        assertTrue(result.degraded());
        assertEquals(1, result.loadout().items().size());
        assertEquals("minecraft:stone", result.loadout().items().get(0).itemId());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("minecraft:removed_item"));
    }

    @Test
    public void countsClampedToVersionStackSize() {
        Loadout loadout = new Loadout("kit", Collections.singletonList(
                new Loadout.Item("minecraft:ender_pearl", 4, 64)));
        LoadoutCompatibility.Result result = LoadoutCompatibility.filter(registries(), loadout);
        assertTrue(result.degraded());
        assertEquals(16, result.loadout().items().get(0).count());
        assertTrue(result.warnings().get(0).contains("16"));
    }

    @Test
    public void emptyLoadoutStaysEmpty() {
        LoadoutCompatibility.Result result = LoadoutCompatibility.filter(registries(),
                new Loadout("kit", null));
        assertFalse(result.degraded());
        assertTrue(result.loadout().items().isEmpty());
    }

    @Test
    public void nullInputsRejected() {
        try {
            LoadoutCompatibility.filter(null, new Loadout("kit", null));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            LoadoutCompatibility.filter(registries(), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
