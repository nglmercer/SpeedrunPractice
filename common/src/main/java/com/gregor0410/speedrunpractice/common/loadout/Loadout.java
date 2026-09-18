package com.gregor0410.speedrunpractice.common.loadout;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.util.SimpleJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named inventory preset. Shared model is NBT-free (namespaced ids + counts);
 * version adapters translate to/from NBT on apply/capture.
 */
public final class Loadout {
    private final String id;
    private final List<Item> items;

    public Loadout(String id, List<Item> items) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("loadout id must not be empty");
        }
        this.id = id.trim();
        this.items = items == null
                ? Collections.<Item>emptyList()
                : Collections.unmodifiableList(new ArrayList<Item>(items));
    }

    public String id() {
        return id;
    }

    public List<Item> items() {
        return items;
    }

    /** Parses preset JSON with readable errors (slot/item problems name the index). */
    public static Loadout fromJson(String json) throws PracticeException {
        Map<String, Object> map;
        try {
            map = SimpleJson.parseObject(json);
        } catch (SimpleJson.JsonException bad) {
            throw new PracticeException("Invalid loadout JSON: " + bad.getMessage(),
                    "That loadout is not valid JSON: " + bad.getMessage());
        }
        return fromMap(map);
    }

    @SuppressWarnings("unchecked")
    public static Loadout fromMap(Map<String, Object> map) throws PracticeException {
        Object rawId = map.get("id");
        if (!(rawId instanceof String) || ((String) rawId).trim().isEmpty()) {
            throw new PracticeException("Loadout is missing its 'id'",
                    "That loadout is missing its \"id\" field.");
        }
        String id = ((String) rawId).trim();
        Object rawItems = map.get("items");
        if (!(rawItems instanceof List)) {
            throw new PracticeException("Loadout '" + id + "' is missing its 'items' array",
                    "Loadout \"" + id + "\" is missing its \"items\" list.");
        }
        List<Item> items = new ArrayList<Item>();
        List<?> list = (List<?>) rawItems;
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            if (!(entry instanceof Map)) {
                throw new PracticeException("Loadout '" + id + "' item #" + i + " is not an object",
                        "Loadout \"" + id + "\": item #" + i + " must be an object.");
            }
            Map<String, Object> item = (Map<String, Object>) entry;
            Object rawItem = item.get("item");
            if (!(rawItem instanceof String) || !((String) rawItem).contains(":")) {
                throw new PracticeException("Loadout '" + id + "' item #" + i + " has no namespaced id",
                        "Loadout \"" + id + "\": item #" + i + " needs an id like \"minecraft:stone\".");
            }
            int slot = asInt(item.get("slot"), -1);
            int count = asInt(item.get("count"), 1);
            if (count < 1 || count > 64) {
                throw new PracticeException("Loadout '" + id + "' item #" + i + " has bad count " + count,
                        "Loadout \"" + id + "\": item #" + i + " count must be 1-64.");
            }
            items.add(new Item(((String) rawItem).trim(), slot, count));
        }
        return new Loadout(id, items);
    }

    public String toJson(boolean pretty) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        List<Object> list = new ArrayList<Object>();
        for (Item item : items) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("item", item.itemId());
            row.put("slot", item.slot());
            row.put("count", item.count());
            list.add(row);
        }
        map.put("items", list);
        return SimpleJson.toJson(map, pretty);
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return fallback;
    }

    /** One stack in a preset. */
    public static final class Item {
        private final String itemId;
        private final int slot;
        private final int count;

        public Item(String itemId, int slot, int count) {
            if (itemId == null || itemId.trim().isEmpty()) {
                throw new IllegalArgumentException("item id must not be empty");
            }
            this.itemId = itemId.trim();
            this.slot = slot;
            this.count = count;
        }

        public String itemId() {
            return itemId;
        }

        public int slot() {
            return slot;
        }

        public int count() {
            return count;
        }
    }

    /** Inventory areas, legacy-compatible numbering. */
    public enum SlotArea {
        MAIN,
        ARMOR,
        OFFHAND
    }

    /** Interprets raw slot numbers: 0-35 main, 100-103 armor, -106 offhand. */
    public static final class InventorySlot {
        private final int raw;

        private InventorySlot(int raw) {
            this.raw = raw;
        }

        public static InventorySlot of(int raw) {
            return new InventorySlot(raw);
        }

        public int raw() {
            return raw;
        }

        public SlotArea area() {
            if (raw == -106) {
                return SlotArea.OFFHAND;
            }
            if (raw >= 100 && raw <= 103) {
                return SlotArea.ARMOR;
            }
            return SlotArea.MAIN;
        }

        /** Zero-based index inside its area. */
        public int index() {
            if (area() == SlotArea.ARMOR) {
                return raw - 100;
            }
            if (area() == SlotArea.OFFHAND) {
                return 0;
            }
            return raw;
        }
    }
}
