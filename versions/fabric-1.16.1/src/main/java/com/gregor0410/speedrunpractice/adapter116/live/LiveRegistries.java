package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.adapter.RegistryAdapter;
import com.gregor0410.speedrunpractice.adapter116.RegistryIds;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.registry.Registry;

/**
 * Item lookups on the live 1.16.1 registry. Normalization is the shared
 * pure helper; existence and stack limits read the real registry.
 */
final class LiveRegistries implements RegistryAdapter {
    @Override
    public String normalizeItemId(String id) {
        return RegistryIds.normalizeItemId(id);
    }

    @Override
    public boolean itemExists(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        try {
            return Registry.ITEM.containsId(new Identifier(normalizeItemId(id)));
        } catch (InvalidIdentifierException bad) {
            return false;
        }
    }

    @Override
    public int maxStackSize(String id) {
        if (!itemExists(id)) {
            return 64;
        }
        try {
            Item item = Registry.ITEM.get(new Identifier(normalizeItemId(id)));
            if (item == null || item == Items.AIR) {
                return 64;
            }
            return Math.max(1, item.getMaxCount());
        } catch (InvalidIdentifierException bad) {
            return 64;
        }
    }
}
