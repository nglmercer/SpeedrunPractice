package com.gregor0410.speedrunpractice.adapter263;

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

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Recording {@link MinecraftAdapter} for delegation tests. No Minecraft types. */
final class FakeMinecraftAdapter implements MinecraftAdapter {
    private final Set<Capability> supported = EnumSet.noneOf(Capability.class);
    private PracticeCommands.Node registered;
    private CommandAdapter.CommandExecutor executor;

    private final WorldAdapter worlds = new WorldAdapter() {
        @Override
        public PracticeWorld createPracticeWorld(long seed, PracticeWorldOptions options) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void deletePracticeWorld(PracticeWorld world) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void resetPracticeWorld(PracticeWorld world, long seed, PracticeWorldOptions options) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public PracticePosition spawnPosition(PracticeWorld world) {
            throw new UnsupportedOperationException("fake");
        }
    };

    private final PlayerAdapter players = new PlayerAdapter() {
        @Override
        public void teleport(PracticePlayer player, PracticePosition position) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void setHealth(PracticePlayer player, double health) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void setFood(PracticePlayer player, int food) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void clearEffects(PracticePlayer player) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void applyLoadout(PracticePlayer player, Loadout loadout) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void resetPlayer(PracticePlayer player) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public PracticePosition getPosition(PracticePlayer player) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public double getHealth(PracticePlayer player) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public int getFood(PracticePlayer player) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public PracticeWorld getWorld(PracticePlayer player) {
            throw new UnsupportedOperationException("fake");
        }
    };

    private final InventoryAdapter inventories = new InventoryAdapter() {
        @Override
        public void applyLoadout(PracticePlayer player, Loadout loadout) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public Loadout captureLoadout(PracticePlayer player, String id) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void clear(PracticePlayer player) {
            throw new UnsupportedOperationException("fake");
        }
    };

    private final StructureAdapter structures = new StructureAdapter() {
        @Override
        public Optional<StructureLocation> locateNearest(PracticeWorld world, StructureQuery query) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public List<StructureLocation> locate(PracticeWorld world, StructureQuery query, int limit) {
            throw new UnsupportedOperationException("fake");
        }
    };

    private final PortalAdapter portals = new PortalAdapter() {
        @Override
        public void createNetherPortal(PracticeWorld world, PracticePosition position) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void linkPortals(PracticeWorld overworld, PracticeWorld nether, PracticePosition overworldPos) {
            throw new UnsupportedOperationException("fake");
        }
    };

    private final DragonAdapter dragons = new DragonAdapter() {
        @Override
        public void resetFight(PracticeWorld world) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void forcePerch(PracticeWorld world) {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public boolean hasLivingDragon(PracticeWorld world) {
            throw new UnsupportedOperationException("fake");
        }
    };

    private final RegistryAdapter registries = new RegistryAdapter() {
        @Override
        public String normalizeItemId(String id) {
            return RegistryIds.normalizeItemId(id);
        }

        @Override
        public boolean itemExists(String id) {
            return false;
        }

        @Override
        public int maxStackSize(String id) {
            return 64;
        }
    };

    private final CommandAdapter commands = new CommandAdapter() {
        @Override
        public void register(PracticeCommands.Node root, CommandExecutor executor) {
            registered = root;
            FakeMinecraftAdapter.this.executor = executor;
        }
    };

    private final GuiAdapter gui = new GuiAdapter() {
        @Override
        public void openMainMenu(PracticePlayer player) throws PracticeException {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void openScenarioScreen(PracticePlayer player, PracticePreset preset) throws PracticeException {
            throw new UnsupportedOperationException("fake");
        }

        @Override
        public void openResultsScreen(PracticePlayer player, PracticeResult result) throws PracticeException {
            throw new UnsupportedOperationException("fake");
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

    // Like every other fake here, seeds never match: the shell tests only
    // need an identity to check delegation; real analysis belongs to
    // SeedAnalyzer263 (unrunnable without the game).
    private final SeedAnalyzer seeds = new SeedAnalyzer() {
        @Override
        public SeedAnalysis analyze(long seed, SeedQuery query) {
            return SeedAnalysis.mismatch();
        }

        @Override
        public boolean matches(long seed, SeedQuery query) {
            return false;
        }
    };

    void claim(Capability capability) {
        supported.add(capability);
    }

    PracticeCommands.Node registered() {
        return registered;
    }

    CommandAdapter.CommandExecutor executor() {
        return executor;
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
        return supported.contains(capability);
    }
}
