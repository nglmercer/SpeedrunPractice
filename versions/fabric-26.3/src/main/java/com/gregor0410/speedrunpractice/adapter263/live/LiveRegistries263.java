package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.adapter263.RegistryIds;
import com.gregor0410.speedrunpractice.common.adapter.RegistryAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Item lookups on the live 26.3 registry. Normalization is the shared
 * pure helper; existence and stack limits read the real registry.
 */
final class LiveRegistries263 implements RegistryAdapter {
    @Override
    public String normalizeItemId(String id) {
        return RegistryIds.normalizeItemId(id);
    }

    @Override
    public boolean itemExists(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        Identifier parsed = Identifier.tryParse(normalizeItemId(id));
        return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed);
    }

    @Override
    public int maxStackSize(String id) {
        if (!itemExists(id)) {
            return 64;
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(normalizeItemId(id)));
        if (item == null || item == Items.AIR) {
            return 64;
        }
        return Math.max(1, item.getDefaultMaxStackSize());
    }
}
