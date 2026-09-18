package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;

/**
 * Inventory capture/apply. The shared {@link Loadout} model stays NBT-free;
 * each version translates to/from its own NBT here.
 */
public interface InventoryAdapter {
    void applyLoadout(PracticePlayer player, Loadout loadout) throws PracticeException;

    /** Captures the live inventory as a shareable preset (skips unknown NBT safely). */
    Loadout captureLoadout(PracticePlayer player, String id) throws PracticeException;

    void clear(PracticePlayer player) throws PracticeException;
}
