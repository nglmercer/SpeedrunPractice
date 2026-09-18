package com.gregor0410.speedrunpractice.common.loadout;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LoadoutManagerTest {
    private static final String JSON = "{\"id\": \"bastion_default\", \"items\": ["
            + "{\"item\": \"minecraft:iron_pickaxe\", \"slot\": 0, \"count\": 1},"
            + "{\"item\": \"minecraft:bread\", \"slot\": 1, \"count\": 16}]}";

    @Test
    public void importExportRoundTrip() throws Exception {
        LoadoutManager manager = new LoadoutManager.InMemoryLoadoutManager();
        Loadout loadout = manager.importLoadout(JSON);
        assertEquals("bastion_default", loadout.id());
        assertEquals(2, loadout.items().size());
        assertEquals(1, manager.list().size());
        Loadout back = Loadout.fromJson(manager.exportJson("bastion_default"));
        assertEquals("bastion_default", back.id());
        assertEquals(2, back.items().size());
    }

    @Test
    public void duplicateRenameDelete() throws Exception {
        LoadoutManager manager = new LoadoutManager.InMemoryLoadoutManager();
        manager.importLoadout(JSON);
        manager.duplicate("bastion_default", "bastion_copy");
        assertEquals(2, manager.list().size());
        manager.rename("bastion_copy", "bastion_v2");
        assertNull(manager.get("bastion_copy"));
        assertTrue(manager.delete("bastion_v2"));
        assertFalse(manager.delete("missing"));
    }

    @Test
    public void badJsonIsRejectedReadably() {
        LoadoutManager manager = new LoadoutManager.InMemoryLoadoutManager();
        try {
            manager.importLoadout("{nope");
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("JSON"));
        }
        try {
            manager.importLoadout("{\"items\": []}");
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("id"));
        }
        try {
            manager.importLoadout("{\"id\": \"x\", \"items\": [{\"item\": \"stone\", \"slot\": 0}]}");
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("minecraft:stone"));
        }
    }

    @Test
    public void slotAreasMatchLegacyNumbering() {
        assertEquals(Loadout.SlotArea.MAIN, Loadout.InventorySlot.of(5).area());
        assertEquals(5, Loadout.InventorySlot.of(5).index());
        assertEquals(Loadout.SlotArea.ARMOR, Loadout.InventorySlot.of(101).area());
        assertEquals(1, Loadout.InventorySlot.of(101).index());
        assertEquals(Loadout.SlotArea.OFFHAND, Loadout.InventorySlot.of(-106).area());
    }
}
