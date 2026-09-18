package com.gregor0410.speedrunpractice.testsupport;

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
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link MinecraftAdapter} for engine/scenario unit tests: scripted
 * structures, dragons and analyzer verdicts, recorded GUI/command/timer calls.
 */
public final class ScenarioTestHarness implements MinecraftAdapter {
    private final GameVersion version;
    private final AtomicLong handles = new AtomicLong();
    private final Map<String, FakeWorld> worlds = new HashMap<String, FakeWorld>();
    private final Map<String, PracticePosition> positions = new HashMap<String, PracticePosition>();
    private final Map<String, Double> health = new HashMap<String, Double>();
    private final Map<String, Integer> food = new HashMap<String, Integer>();
    private final Map<String, Loadout> inventories = new HashMap<String, Loadout>();
    private final Map<String, StructureAdapter.StructureLocation> structures =
            new HashMap<String, StructureAdapter.StructureLocation>();
    private final Map<String, Boolean> dragons = new HashMap<String, Boolean>();
    private final List<String> guiCalls = new ArrayList<String>();
    private final List<String> portalCalls = new ArrayList<String>();
    private final List<String> timerCalls = new ArrayList<String>();
    private PracticeCommands.Node commandRoot;
    private CommandAdapter.CommandExecutor commandExecutor;
    private SeedAnalyzer analyzer = new SeedAnalyzer() {
        @Override
        public SeedAnalysis analyze(long seed, SeedQuery query) {
            return SeedAnalysis.of(true, null);
        }

        @Override
        public boolean matches(long seed, SeedQuery query) {
            return true;
        }
    };

    public ScenarioTestHarness() {
        this(GameVersion.MC_1_16_1);
    }

    public ScenarioTestHarness(GameVersion version) {
        this.version = version;
    }

    public static FakePlayer player(String handle) {
        return new FakePlayer(handle);
    }

    public void scriptStructure(String structureId, PracticePosition position, Map<String, String> metadata) {
        structures.put(structureId, new StructureAdapter.StructureLocation(structureId, position, metadata));
    }

    public void setLivingDragon(String worldHandle, boolean living) {
        dragons.put(worldHandle, living);
    }

    public void setAnalyzer(SeedAnalyzer analyzer) {
        if (analyzer == null) {
            throw new IllegalArgumentException("analyzer must not be null");
        }
        this.analyzer = analyzer;
    }

    public Loadout appliedLoadout(String playerHandle) {
        return inventories.get(playerHandle);
    }

    public PracticePosition positionOf(String playerHandle) {
        return positions.get(playerHandle);
    }

    public List<String> guiCalls() {
        return Collections.unmodifiableList(guiCalls);
    }

    public List<String> portalCalls() {
        return Collections.unmodifiableList(portalCalls);
    }

    public List<String> timerCalls() {
        return Collections.unmodifiableList(timerCalls);
    }

    public PracticeCommands.Node commandRoot() {
        return commandRoot;
    }

    @Override
    public GameVersion version() {
        return version;
    }

    @Override
    public boolean supports(Capability capability) {
        return true;
    }

    @Override
    public SeedAnalyzer seeds() {
        return analyzer;
    }

    @Override
    public WorldAdapter worlds() {
        return new WorldAdapter() {
            @Override
            public PracticeWorld createPracticeWorld(long seed, PracticeWorldOptions options) {
                FakeWorld world = new FakeWorld("world-" + handles.incrementAndGet(), seed,
                        options.dimension(), version);
                worlds.put(world.handleId(), world);
                return world;
            }

            @Override
            public void deletePracticeWorld(PracticeWorld world) {
                worlds.remove(world.handleId());
            }

            @Override
            public void resetPracticeWorld(PracticeWorld world, long seed, PracticeWorldOptions options) {
                worlds.put(world.handleId(), new FakeWorld(world.handleId(), seed, options.dimension(), version));
            }

            @Override
            public PracticePosition spawnPosition(PracticeWorld world) {
                return new PracticePosition(0.0, 64.0, 0.0, 90.0f, 0.0f);
            }
        };
    }

    @Override
    public PlayerAdapter players() {
        return new PlayerAdapter() {
            @Override
            public void teleport(PracticePlayer player, PracticePosition position) {
                positions.put(player.handleId(), position);
            }

            @Override
            public void setHealth(PracticePlayer player, double value) {
                health.put(player.handleId(), value);
            }

            @Override
            public void setFood(PracticePlayer player, int value) {
                food.put(player.handleId(), value);
            }

            @Override
            public void clearEffects(PracticePlayer player) {
            }

            @Override
            public void applyLoadout(PracticePlayer player, Loadout loadout) {
                inventories.put(player.handleId(), loadout);
            }

            @Override
            public void resetPlayer(PracticePlayer player) {
                health.put(player.handleId(), 20.0);
                food.put(player.handleId(), 20);
            }

            @Override
            public PracticePosition getPosition(PracticePlayer player) {
                PracticePosition position = positions.get(player.handleId());
                return position == null ? new PracticePosition(0.0, 64.0, 0.0) : position;
            }

            @Override
            public double getHealth(PracticePlayer player) {
                Double value = health.get(player.handleId());
                return value == null ? 20.0 : value;
            }

            @Override
            public int getFood(PracticePlayer player) {
                Integer value = food.get(player.handleId());
                return value == null ? 20 : value;
            }

            @Override
            public PracticeWorld getWorld(PracticePlayer player) throws PracticeException {
                if (worlds.isEmpty()) {
                    throw new PracticeException.AdapterException("Harness has no worlds yet",
                            "Test harness has no world yet.");
                }
                return worlds.values().iterator().next();
            }
        };
    }

    @Override
    public InventoryAdapter inventories() {
        return new InventoryAdapter() {
            @Override
            public void applyLoadout(PracticePlayer player, Loadout loadout) {
                inventories.put(player.handleId(), loadout);
            }

            @Override
            public Loadout captureLoadout(PracticePlayer player, String id) {
                Loadout current = inventories.get(player.handleId());
                return current == null ? new Loadout(id, null) : new Loadout(id, current.items());
            }

            @Override
            public void clear(PracticePlayer player) {
                inventories.remove(player.handleId());
            }
        };
    }

    @Override
    public StructureAdapter structures() {
        return new StructureAdapter() {
            @Override
            public Optional<StructureLocation> locateNearest(PracticeWorld world, StructureQuery query) {
                return Optional.ofNullable(structures.get(query.structureId()));
            }

            @Override
            public List<StructureLocation> locate(PracticeWorld world, StructureQuery query, int limit) {
                StructureLocation location = structures.get(query.structureId());
                if (location == null || limit < 1) {
                    return Collections.emptyList();
                }
                return Collections.singletonList(location);
            }
        };
    }

    @Override
    public PortalAdapter portals() {
        return new PortalAdapter() {
            @Override
            public void createNetherPortal(PracticeWorld world, PracticePosition position) {
                portalCalls.add("create:" + world.handleId() + ":" + position);
            }

            @Override
            public void linkPortals(PracticeWorld overworld, PracticeWorld nether, PracticePosition overworldPos) {
                portalCalls.add("link:" + overworld.handleId() + ":" + nether.handleId());
            }
        };
    }

    @Override
    public DragonAdapter dragons() {
        return new DragonAdapter() {
            @Override
            public void resetFight(PracticeWorld world) {
                dragons.put(world.handleId(), Boolean.TRUE);
            }

            @Override
            public void forcePerch(PracticeWorld world) {
                guiCalls.add("forcePerch:" + world.handleId());
            }

            @Override
            public boolean hasLivingDragon(PracticeWorld world) {
                Boolean living = dragons.get(world.handleId());
                return living == null || living;
            }
        };
    }

    @Override
    public RegistryAdapter registries() {
        return new RegistryAdapter() {
            @Override
            public String normalizeItemId(String id) {
                return id.contains(":") ? id : "minecraft:" + id;
            }

            @Override
            public boolean itemExists(String id) {
                return true;
            }

            @Override
            public int maxStackSize(String id) {
                return 64;
            }
        };
    }

    @Override
    public CommandAdapter commands() {
        return new CommandAdapter() {
            @Override
            public void register(PracticeCommands.Node root, CommandExecutor executor) {
                commandRoot = root;
                commandExecutor = executor;
            }
        };
    }

    @Override
    public GuiAdapter gui() {
        return new GuiAdapter() {
            @Override
            public void openMainMenu(PracticePlayer player) {
                guiCalls.add("mainMenu");
            }

            @Override
            public void openScenarioScreen(PracticePlayer player, PracticePreset preset) {
                guiCalls.add("scenario:" + preset.id());
            }

            @Override
            public void openResultsScreen(PracticePlayer player, PracticeResult result) {
                guiCalls.add("results:" + result.practice());
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
    }

    @Override
    public TimerAdapter timer() {
        return new TimerAdapter() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public void resetTimer() {
                timerCalls.add("reset");
            }

            @Override
            public void startTimer() {
                timerCalls.add("start");
            }

            @Override
            public void stopTimer() {
                timerCalls.add("stop");
            }

            @Override
            public void pauseTimer() {
                timerCalls.add("pause");
            }
        };
    }

    public static final class FakeWorld implements PracticeWorld {
        private final String handleId;
        private final long seed;
        private final PracticeDimension dimension;
        private final GameVersion version;

        public FakeWorld(String handleId, long seed, PracticeDimension dimension, GameVersion version) {
            this.handleId = handleId;
            this.seed = seed;
            this.dimension = dimension;
            this.version = version;
        }

        @Override
        public String handleId() {
            return handleId;
        }

        @Override
        public long seed() {
            return seed;
        }

        @Override
        public PracticeDimension dimension() {
            return dimension;
        }

        @Override
        public GameVersion version() {
            return version;
        }
    }

    public static final class FakePlayer implements PracticePlayer {
        private final String handleId;

        public FakePlayer(String handleId) {
            this.handleId = handleId;
        }

        @Override
        public String handleId() {
            return handleId;
        }
    }
}
