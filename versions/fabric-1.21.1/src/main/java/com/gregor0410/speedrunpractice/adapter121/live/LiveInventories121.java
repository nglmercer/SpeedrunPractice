package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.adapter.InventoryAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory presets on the live player inventory. Slot numbering is the
 * legacy scheme ({@code 0-35} main, {@code 100-103} armor, {@code -106}
 * offhand); unknown items are skipped with a warning and counts clamp to
 * the version's max stack size, so one missing item degrades the kit
 * instead of breaking the practice.
 */
final class LiveInventories121 implements InventoryAdapter {
    private final LiveAdapter121 adapter;

    LiveInventories121(LiveAdapter121 adapter) {
        this.adapter = adapter;
    }

    @Override
    public void applyLoadout(PracticePlayer player, Loadout loadout) throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "applyLoadout");
        if (loadout == null) {
            throw new IllegalArgumentException("loadout must not be null");
        }
        entity.getInventory().clear();
        for (Loadout.Item item : loadout.items()) {
            String id = adapter.registries().normalizeItemId(item.itemId());
            Item live;
            try {
                live = Registries.ITEM.get(Identifier.of(id));
            } catch (RuntimeException bad) {
                SpeedrunLogger.warn("Skipping \"" + item.itemId() + "\": bad item id");
                continue;
            }
            if (live == null || live == Items.AIR) {
                SpeedrunLogger.warn("Skipping \"" + item.itemId() + "\": no such item on 1.21.1");
                continue;
            }
            int count = Math.max(1, Math.min(item.count(), live.getMaxCount()));
            if (!place(entity, item.slot(), new ItemStack(live, count))) {
                SpeedrunLogger.warn("Skipping \"" + item.itemId() + "\": bad slot " + item.slot());
            }
        }
        entity.playerScreenHandler.sendContentUpdates();
    }

    @Override
    public Loadout captureLoadout(PracticePlayer player, String id) throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "captureLoadout");
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("loadout id must not be empty");
        }
        List<Loadout.Item> items = new ArrayList<Loadout.Item>();
        for (int slot = 0; slot < entity.getInventory().main.size(); slot++) {
            ItemStack stack = entity.getInventory().main.get(slot);
            if (!stack.isEmpty()) {
                items.add(new Loadout.Item(Registries.ITEM.getId(stack.getItem()).toString(),
                        slot, stack.getCount()));
            }
        }
        for (int slot = 0; slot < entity.getInventory().armor.size(); slot++) {
            ItemStack stack = entity.getInventory().armor.get(slot);
            if (!stack.isEmpty()) {
                items.add(new Loadout.Item(Registries.ITEM.getId(stack.getItem()).toString(),
                        100 + slot, stack.getCount()));
            }
        }
        for (int slot = 0; slot < entity.getInventory().offHand.size(); slot++) {
            ItemStack stack = entity.getInventory().offHand.get(slot);
            if (!stack.isEmpty()) {
                items.add(new Loadout.Item(Registries.ITEM.getId(stack.getItem()).toString(),
                        -106, stack.getCount()));
            }
        }
        return new Loadout(id.trim(), items);
    }

    @Override
    public void clear(PracticePlayer player) throws PracticeException {
        ServerPlayerEntity entity = requireEntity(player, "clear");
        entity.getInventory().clear();
        entity.playerScreenHandler.sendContentUpdates();
    }

    private static boolean place(ServerPlayerEntity entity, int slot, ItemStack stack) {
        Loadout.InventorySlot area = Loadout.InventorySlot.of(slot);
        switch (area.area()) {
            case MAIN:
                if (slot < 0 || slot >= entity.getInventory().main.size()) {
                    return false;
                }
                entity.getInventory().main.set(slot, stack);
                return true;
            case ARMOR:
                if (area.index() < 0 || area.index() >= entity.getInventory().armor.size()) {
                    return false;
                }
                entity.getInventory().armor.set(area.index(), stack);
                return true;
            case OFFHAND:
                entity.getInventory().offHand.set(0, stack);
                return true;
            default:
                return false;
        }
    }

    private static ServerPlayerEntity requireEntity(PracticePlayer player, String operation)
            throws PracticeException {
        if (!(player instanceof LivePlayer121)) {
            throw new PracticeException(operation + " got a foreign player handle",
                    "That player belongs to another session. Stop and start the practice again.");
        }
        return ((LivePlayer121) player).entity();
    }
}
