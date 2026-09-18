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
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeResult;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.commands.PracticeCommands;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.List;
import java.util.Optional;

/**
 * 1.16.1 adapter set (primary speedrunning build). Migration target: methods
 * that need live Minecraft calls fail with a domain {@link PracticeException}
 * until the Loom wiring lands (see versions/README.md); the untouched legacy
 * runtime in root {@code src/} stays authoritative in-game meanwhile.
 */
public final class AdapterSet116 implements MinecraftAdapter {
    private static final String PENDING = "The 1.16.1 adapter is not wired to the live game yet.";

    private PracticeCommands.Node registeredCommands;
    private CommandAdapter.CommandExecutor commandExecutor;

    private final WorldAdapter worlds = new WorldAdapter() {
        @Override
        public PracticeWorld createPracticeWorld(long seed, PracticeWorldOptions options) throws PracticeException {
            // Migration target: delegate to IMinecraftServer#createLinkedPracticeWorld /
            // #createEndPracticeWorld from the legacy runtime once this module gains Loom.
            throw pending("createPracticeWorld");
        }

        @Override
        public void deletePracticeWorld(PracticeWorld world) throws PracticeException {
            throw pending("deletePracticeWorld");
        }

        @Override
        public void resetPracticeWorld(PracticeWorld world, long seed, PracticeWorldOptions options)
                throws PracticeException {
            throw pending("resetPracticeWorld");
        }

        @Override
        public PracticePosition spawnPosition(PracticeWorld world) throws PracticeException {
            throw pending("spawnPosition");
        }
    };

    private final PlayerAdapter players = new PlayerAdapter() {
        @Override
        public void teleport(PracticePlayer player, PracticePosition position) throws PracticeException {
            throw pending("teleport");
        }

        @Override
        public void setHealth(PracticePlayer player, double health) throws PracticeException {
            throw pending("setHealth");
        }

        @Override
        public void setFood(PracticePlayer player, int food) throws PracticeException {
            throw pending("setFood");
        }

        @Override
        public void clearEffects(PracticePlayer player) throws PracticeException {
            throw pending("clearEffects");
        }

        @Override
        public void applyLoadout(PracticePlayer player, Loadout loadout) throws PracticeException {
            throw pending("applyLoadout");
        }

        @Override
        public void resetPlayer(PracticePlayer player) throws PracticeException {
            throw pending("resetPlayer");
        }

        @Override
        public PracticePosition getPosition(PracticePlayer player) throws PracticeException {
            throw pending("getPosition");
        }

        @Override
        public double getHealth(PracticePlayer player) throws PracticeException {
            throw pending("getHealth");
        }

        @Override
        public int getFood(PracticePlayer player) throws PracticeException {
            throw pending("getFood");
        }

        @Override
        public PracticeWorld getWorld(PracticePlayer player) throws PracticeException {
            throw pending("getWorld");
        }
    };

    private final InventoryAdapter inventories = new InventoryAdapter() {
        @Override
        public void applyLoadout(PracticePlayer player, Loadout loadout) throws PracticeException {
            throw pending("inventory.applyLoadout");
        }

        @Override
        public Loadout captureLoadout(PracticePlayer player, String id) throws PracticeException {
            throw pending("inventory.captureLoadout");
        }

        @Override
        public void clear(PracticePlayer player) throws PracticeException {
            throw pending("inventory.clear");
        }
    };

    private final StructureAdapter structures = new StructureAdapter() {
        @Override
        public Optional<StructureLocation> locateNearest(PracticeWorld world, StructureQuery query)
                throws PracticeException {
            throw pending("locateNearest");
        }

        @Override
        public List<StructureLocation> locate(PracticeWorld world, StructureQuery query, int limit)
                throws PracticeException {
            throw pending("locate");
        }
    };

    private final PortalAdapter portals = new PortalAdapter() {
        @Override
        public void createNetherPortal(PracticeWorld world, PracticePosition position) throws PracticeException {
            throw pending("createNetherPortal");
        }

        @Override
        public void linkPortals(PracticeWorld overworld, PracticeWorld nether, PracticePosition overworldPos)
                throws PracticeException {
            throw pending("linkPortals");
        }
    };

    private final DragonAdapter dragons = new DragonAdapter() {
        @Override
        public void resetFight(PracticeWorld world) throws PracticeException {
            throw pending("resetFight");
        }

        @Override
        public void forcePerch(PracticeWorld world) throws PracticeException {
            throw pending("forcePerch");
        }

        @Override
        public boolean hasLivingDragon(PracticeWorld world) throws PracticeException {
            throw pending("hasLivingDragon");
        }
    };

    private final RegistryAdapter registries = new RegistryAdapter() {
        @Override
        public String normalizeItemId(String id) {
            if (id == null) {
                return "minecraft:air";
            }
            String trimmed = id.trim().toLowerCase();
            return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
        }

        @Override
        public boolean itemExists(String id) {
            // Optimistic until live registries are reachable; unknown ids are
            // skipped with a warning at apply time, never fatal.
            return id != null && !id.trim().isEmpty();
        }

        @Override
        public int maxStackSize(String id) {
            return 64;
        }
    };

    private final CommandAdapter commands = new CommandAdapter() {
        @Override
        public void register(PracticeCommands.Node root, CommandExecutor executor) {
            if (root == null || executor == null) {
                throw new IllegalArgumentException("command root and executor must not be null");
            }
            // Retained for the Loom wiring step so the entrypoint can build
            // Brigadier nodes from the shared tree; legacy dispatcher stays
            // live meanwhile.
            registeredCommands = root;
            commandExecutor = executor;
            SpeedrunLogger.info("Registered /" + root.name() + " command model (" + root.children().size()
                    + " children) for 1.16.1");
        }
    };

    private final GuiAdapter gui = new GuiAdapter() {
        @Override
        public void openMainMenu(PracticePlayer player) throws PracticeException {
            throw pending("openMainMenu");
        }

        @Override
        public void openScenarioScreen(PracticePlayer player, PracticePreset preset) throws PracticeException {
            throw pending("openScenarioScreen");
        }

        @Override
        public void openResultsScreen(PracticePlayer player, PracticeResult result) throws PracticeException {
            throw pending("openResultsScreen");
        }

        @Override
        public boolean isAvailable() {
            return false;
        }
    };

    private final TimerAdapter timer = new TimerAdapter() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void resetTimer() {
        }

        @Override
        public void startTimer() {
        }

        @Override
        public void stopTimer() {
        }

        @Override
        public void pauseTimer() {
        }
    };

    private final SeedAnalyzer analyzer = new SeedAnalyzer() {
        @Override
        public SeedAnalysis analyze(long seed, SeedQuery query) {
            // TODO(loom): real 1.16.1 seed math; unconstrained queries trivially match.
            return SeedAnalysis.of(matches(seed, query), null);
        }

        @Override
        public boolean matches(long seed, SeedQuery query) {
            return query.requiredBiome() == null && query.requiredStructures().isEmpty();
        }
    };

    private static PracticeException.AdapterException pending(String operation) {
        return new PracticeException.AdapterException("1.16.1 adapter: " + operation + " is pending Loom wiring",
                PENDING + " (" + operation + ")");
    }

    /**
     * Last command tree handed to {@link CommandAdapter#register}, or null
     * when nothing was registered yet. The Loom entrypoint reads this to
     * build Brigadier nodes once the live game is reachable.
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
        return worlds;
    }

    @Override
    public PlayerAdapter players() {
        return players;
    }

    @Override
    public InventoryAdapter inventories() {
        return inventories;
    }

    @Override
    public StructureAdapter structures() {
        return structures;
    }

    @Override
    public PortalAdapter portals() {
        return portals;
    }

    @Override
    public DragonAdapter dragons() {
        return dragons;
    }

    @Override
    public RegistryAdapter registries() {
        return registries;
    }

    @Override
    public CommandAdapter commands() {
        return commands;
    }

    @Override
    public GuiAdapter gui() {
        return gui;
    }

    @Override
    public TimerAdapter timer() {
        return timer;
    }

    @Override
    public SeedAnalyzer seeds() {
        return analyzer;
    }

    @Override
    public boolean supports(Capability capability) {
        // Plan sections 7 and 98: a capability may return true only when the
        // implementation exists, compiles, and passed in-game testing on this
        // version. Every live method below still throws pending(), so claiming
        // support would crash GUI/command flows that trust this flag.
        return false;
    }
}
