package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.adapter263.AdapterSet263;
import com.gregor0410.speedrunpractice.adapter263.RegistryIds;
import com.gregor0410.speedrunpractice.adapter263.Seeds263;
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
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;

/**
 * Live 26.3 adapter behind the {@link AdapterSet263} shell. Step 17a wires
 * commands (plus interim seeds/registry so searches run); worlds, players,
 * inventories, structures, portals, dragons and GUI stay pending until
 * their slices land. Single-player scope: at most one server, one
 * practicing player, and one active practice at a time.
 */
public final class LiveAdapter263 implements MinecraftAdapter {
    private static final String PENDING = "The 26.3 adapter is not ported yet.";

    private volatile MinecraftServer server;
    private final CommandAdapter commands = new LiveCommands263();
    private final SeedAnalyzer seeds = new Seeds263();

    private final RegistryAdapter registries = new RegistryAdapter() {
        @Override
        public String normalizeItemId(String id) {
            return RegistryIds.normalizeItemId(id);
        }

        @Override
        public boolean itemExists(String id) {
            return id != null && !id.trim().isEmpty();
        }

        @Override
        public int maxStackSize(String id) {
            return 64;
        }
    };

    private final WorldAdapter worlds = new WorldAdapter() {
        @Override
        public PracticeWorld createPracticeWorld(long seed, PracticeWorldOptions options) throws PracticeException {
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

        @Override
        public PracticeCheckpoint.PlayerSnapshot capturePlayerState(PracticePlayer player) throws PracticeException {
            throw pending("capturePlayerState");
        }

        @Override
        public void restorePlayerState(PracticePlayer player, PracticeCheckpoint.PlayerSnapshot snapshot)
                throws PracticeException {
            throw pending("restorePlayerState");
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

    /** The server currently running the game, or null outside a session. */
    public MinecraftServer server() {
        return server;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public GameVersion version() {
        return GameVersion.V_26_3;
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
        return seeds;
    }

    @Override
    public boolean supports(Capability capability) {
        // Nothing claimed until validated against 26.3 behaviour.
        return false;
    }

    private static PracticeException.AdapterException pending(String operation) {
        return new PracticeException.AdapterException("26.3 adapter: " + operation + " is pending the port",
                PENDING + " (" + operation + ")");
    }
}
