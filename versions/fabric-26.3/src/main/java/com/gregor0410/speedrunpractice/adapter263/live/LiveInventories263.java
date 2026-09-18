package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.adapter.InventoryAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Inventory presets on the live player inventory. Slot numbering is the
 * legacy scheme ({@code 0-35} main, {@code 100-103} armor feet-first,
 * {@code -106} offhand); unknown items are skipped with a warning and
 * counts clamp to the version's max stack size, so one missing item
 * degrades the kit instead of breaking the practice.
 */
final class LiveInventories263 implements InventoryAdapter {
    private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private final LiveAdapter263 adapter;

    LiveInventories263(LiveAdapter263 adapter) {
        this.adapter = adapter;
    }

    @Override
    public void applyLoadout(PracticePlayer player, Loadout loadout) throws PracticeException {
        ServerPlayer entity = requireEntity(player, "applyLoadout");
        if (loadout == null) {
            throw new IllegalArgumentException("loadout must not be null");
        }
        clear(entity);
        for (Loadout.Item item : loadout.items()) {
            String id = adapter.registries().normalizeItemId(item.itemId());
            Item live;
            try {
                Identifier parsed = Identifier.tryParse(id);
                Optional<? extends net.minecraft.core.Holder<Item>> found =
                        parsed == null ? Optional.<net.minecraft.core.Holder<Item>>empty()
                                : BuiltInRegistries.ITEM.get(parsed);
                live = found.isPresent() ? found.get().value() : null;
            } catch (RuntimeException bad) {
                SpeedrunLogger.warn("Skipping \"" + item.itemId() + "\": bad item id");
                continue;
            }
            if (live == null || live == Items.AIR) {
                SpeedrunLogger.warn("Skipping \"" + item.itemId() + "\": no such item on 26.3");
                continue;
            }
            int count = Math.max(1, Math.min(item.count(), live.getDefaultMaxStackSize()));
            if (!place(entity, item.slot(), new ItemStack(live, count))) {
                SpeedrunLogger.warn("Skipping \"" + item.itemId() + "\": bad slot " + item.slot());
            }
        }
        entity.inventoryMenu.broadcastChanges();
    }

    @Override
    public Loadout captureLoadout(PracticePlayer player, String id) throws PracticeException {
        ServerPlayer entity = requireEntity(player, "captureLoadout");
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("loadout id must not be empty");
        }
        List<Loadout.Item> items = new ArrayList<Loadout.Item>();
        NonNullList<ItemStack> main = entity.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < main.size(); slot++) {
            ItemStack stack = main.get(slot);
            if (!stack.isEmpty()) {
                items.add(new Loadout.Item(itemId(stack), slot, stack.getCount()));
            }
        }
        for (int slot = 0; slot < ARMOR.length; slot++) {
            ItemStack stack = entity.getItemBySlot(ARMOR[slot]);
            if (!stack.isEmpty()) {
                items.add(new Loadout.Item(itemId(stack), 100 + slot, stack.getCount()));
            }
        }
        ItemStack offhand = entity.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!offhand.isEmpty()) {
            items.add(new Loadout.Item(itemId(offhand), -106, offhand.getCount()));
        }
        return new Loadout(id.trim(), items);
    }

    @Override
    public void clear(PracticePlayer player) throws PracticeException {
        ServerPlayer entity = requireEntity(player, "clear");
        clear(entity);
        entity.inventoryMenu.broadcastChanges();
    }

    private static void clear(ServerPlayer entity) {
        entity.getInventory().clearContent();
        for (EquipmentSlot slot : ARMOR) {
            entity.setItemSlot(slot, ItemStack.EMPTY);
        }
        entity.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean place(ServerPlayer entity, int slot, ItemStack stack) {
        Loadout.InventorySlot area = Loadout.InventorySlot.of(slot);
        switch (area.area()) {
            case MAIN:
                NonNullList<ItemStack> main = entity.getInventory().getNonEquipmentItems();
                if (slot < 0 || slot >= main.size()) {
                    return false;
                }
                main.set(slot, stack);
                return true;
            case ARMOR:
                if (area.index() < 0 || area.index() >= ARMOR.length) {
                    return false;
                }
                entity.setItemSlot(ARMOR[area.index()], stack);
                return true;
            case OFFHAND:
                entity.setItemSlot(EquipmentSlot.OFFHAND, stack);
                return true;
            default:
                return false;
        }
    }

    private static ServerPlayer requireEntity(PracticePlayer player, String operation)
            throws PracticeException {
        if (!(player instanceof LivePlayer263)) {
            throw new PracticeException(operation + " got a foreign player handle",
                    "That player belongs to another session. Stop and start the practice again.");
        }
        return ((LivePlayer263) player).entity();
    }
}
