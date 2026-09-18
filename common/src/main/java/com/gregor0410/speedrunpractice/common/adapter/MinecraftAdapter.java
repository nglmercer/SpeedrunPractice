package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;

/**
 * Facade implemented once per Minecraft target
 * ({@code AdapterSet116/121/263}). Shared practice logic talks only to this.
 */
public interface MinecraftAdapter {
    GameVersion version();

    WorldAdapter worlds();

    PlayerAdapter players();

    InventoryAdapter inventories();

    StructureAdapter structures();

    PortalAdapter portals();

    DragonAdapter dragons();

    RegistryAdapter registries();

    CommandAdapter commands();

    GuiAdapter gui();

    TimerAdapter timer();

    SeedAnalyzer seeds();

    /** Explicit per-feature support (plan section 28). */
    boolean supports(Capability capability);
}
