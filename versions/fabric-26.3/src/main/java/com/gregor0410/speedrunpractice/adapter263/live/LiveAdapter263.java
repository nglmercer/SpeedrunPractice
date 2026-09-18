package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.adapter263.AdapterSet263;
import com.gregor0410.speedrunpractice.adapter263.RegistryIds;
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
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Live 26.3 adapter behind the {@link AdapterSet263} shell. Commands,
 * seeds (real worldgen analysis), worlds, players, inventories and interim
 * registry are wired; structures, portals, dragons and GUI stay pending
 * until their slices land. Single-player scope: at most one server, one
 * practicing player, and one active practice at a time.
 */
public final class LiveAdapter263 implements MinecraftAdapter {
    private static final String PENDING = "The 26.3 adapter is not ported yet.";

    private volatile MinecraftServer server;
    private final CommandAdapter commands = new LiveCommands263();
    private final SeedAnalyzer seeds = new SeedAnalyzer263(this);
    private final WorldAdapter worlds = new LiveWorlds263(this);
    private final PlayerAdapter players = new LivePlayers263(this);
    private final InventoryAdapter inventories = new LiveInventories263(this);

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

    private final Map<ResourceKey<Level>, LiveWorld263> byKey =
            Collections.synchronizedMap(new HashMap<ResourceKey<Level>, LiveWorld263>());

    /** The scenario's world: set on create/reset, cleared on delete. */
    private volatile LiveWorld263 currentWorld;

    /** The server currently running the game, or null outside a session. */
    public MinecraftServer server() {
        return server;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        if (server == null) {
            byKey.clear();
            currentWorld = null;
        }
    }

    /** Remembers one created practice world for handle resolution. */
    void track(LiveWorld263 handle) {
        byKey.put(handle.level().dimension(), handle);
    }

    /** Handle for a live level key, or null when untracked. */
    LiveWorld263 tracked(ResourceKey<Level> key) {
        return byKey.get(key);
    }

    LiveWorld263 currentWorld() {
        return currentWorld;
    }

    void setCurrentWorld(LiveWorld263 currentWorld) {
        this.currentWorld = currentWorld;
    }

    /** Forgets a tracked level key. */
    void forget(ResourceKey<Level> key) {
        byKey.remove(key);
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

    /** Resolves the backing level for a practice handle, failing readably. */
    static ServerLevel requireLevel(LiveAdapter263 adapter,
            com.gregor0410.speedrunpractice.common.api.PracticeWorld world, String operation)
            throws PracticeException {
        if (!(world instanceof LiveWorld263)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        ServerLevel backing = ((LiveWorld263) world).level();
        MinecraftServer server = adapter.server();
        if (server == null || server.getLevel(backing.dimension()) == null) {
            throw new PracticeException(operation + " found no live world",
                    "That practice world is gone (the session ended?). Stop and start it again.");
        }
        return backing;
    }
}
