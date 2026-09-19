package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.adapter121.RegistryIds;
import com.gregor0410.speedrunpractice.common.adapter.RegistryAdapter;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;

/**
 * Item lookups on the live 1.21.1 registry. Normalization is the shared
 * pure helper; existence and stack limits read the real registry.
 */
final class LiveRegistries121 implements RegistryAdapter {
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
            return Registries.ITEM.containsId(Identifier.of(normalizeItemId(id)));
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
            Item item = Registries.ITEM.get(Identifier.of(normalizeItemId(id)));
            if (item == null || item == Items.AIR) {
                return 64;
            }
            return Math.max(1, item.getMaxCount());
        } catch (InvalidIdentifierException bad) {
            return 64;
        }
    }
}
