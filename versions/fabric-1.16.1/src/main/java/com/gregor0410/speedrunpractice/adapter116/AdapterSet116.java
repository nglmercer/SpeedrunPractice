package com.gregor0410.speedrunpractice.adapter116;

import com.gregor0410.speedrunpractice.common.adapter.Capability;
import com.gregor0410.speedrunpractice.common.adapter.CommandAdapter;
import com.gregor0410.speedrunpractice.common.adapter.DragonAdapter;
import com.gregor0410.speedrunpractice.common.adapter.GuiAdapter;
import com.gregor0410.speedrunpractice.common.adapter.InventoryAdapter;
import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.adapter.PlayerAdapter;
import com.gregor0410.speedrunpractice.common.adapter.PortalAdapter;
import com.gregor0410.speedrunpractice.common.adapter.RegistryAdapter;
import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.adapter.TimerAdapter;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.commands.PracticeCommands;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

/**
 * 1.16.1 adapter set (primary speedrunning build). Pure delegation shell:
 * every live call forwards to the version-runtime delegate injected by the
 * entrypoint ({@code LiveAdapter116} in this module since the step 12
 * migration). It contains no {@code pending()} fallback — a missing
 * delegate is a wiring bug, not a runtime state.
 */
public final class AdapterSet116 implements MinecraftAdapter {
    private final MinecraftAdapter live;
    private PracticeCommands.Node registeredCommands;
    private CommandAdapter.CommandExecutor commandExecutor;

    private final CommandAdapter commands = new CommandAdapter() {
        @Override
        public void register(PracticeCommands.Node root, CommandExecutor executor) throws PracticeException {
            if (root == null || executor == null) {
                throw new IllegalArgumentException("command root and executor must not be null");
            }
            registeredCommands = root;
            commandExecutor = executor;
            SpeedrunLogger.info("Registering /" + root.name() + " command tree ("
                    + root.children().size() + " children) for 1.16.1");
            live.commands().register(root, executor);
        }
    };

    public AdapterSet116(MinecraftAdapter live) {
        if (live == null) {
            throw new IllegalArgumentException("live delegate must not be null");
        }
        this.live = live;
    }

    /**
     * Last command tree handed to {@link CommandAdapter#register}, or null
     * when nothing was registered yet.
     */
    public PracticeCommands.Node registeredCommands() {
        return registeredCommands;
    }

    /** Executor paired with {@link #registeredCommands()}, or null when idle. */
    public CommandAdapter.CommandExecutor commandExecutor() {
        return commandExecutor;
    }

    @Override
    public GameVersion version() {
        return GameVersion.MC_1_16_1;
    }

    @Override
    public WorldAdapter worlds() {
        return live.worlds();
    }

    @Override
    public PlayerAdapter players() {
        return live.players();
    }

    @Override
    public InventoryAdapter inventories() {
        return live.inventories();
    }

    @Override
    public StructureAdapter structures() {
        return live.structures();
    }

    @Override
    public PortalAdapter portals() {
        return live.portals();
    }

    @Override
    public DragonAdapter dragons() {
        return live.dragons();
    }

    @Override
    public RegistryAdapter registries() {
        return live.registries();
    }

    @Override
    public CommandAdapter commands() {
        return commands;
    }

    @Override
    public GuiAdapter gui() {
        return live.gui();
    }

    @Override
    public TimerAdapter timer() {
        return live.timer();
    }

    @Override
    public SeedAnalyzer seeds() {
        return live.seeds();
    }

    @Override
    public boolean supports(Capability capability) {
        return live.supports(capability);
    }
}
