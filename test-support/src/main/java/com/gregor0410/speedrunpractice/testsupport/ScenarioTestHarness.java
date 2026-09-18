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
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.commands.PracticeCommands;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final Map<String, Float> saturation = new HashMap<String, Float>();
    private final Map<String, Integer> xpLevels = new HashMap<String, Integer>();
    private final Map<String, Integer> xpPoints = new HashMap<String, Integer>();
    private final Map<String, List<String>> effects = new HashMap<String, List<String>>();
    private final Map<String, Integer> selectedSlots = new HashMap<String, Integer>();
    private final Map<String, PracticeDimension> playerDimensions = new HashMap<String, PracticeDimension>();
    private final Map<String, Loadout> inventories = new HashMap<String, Loadout>();
    private final Set<String> missingItems = new HashSet<String>();
    private final Map<String, Integer> stackSizes = new HashMap<String, Integer>();
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

    /** Live practice-world count; for asserting failed starts create nothing. */
    public int worldCount() {
        return worlds.size();
    }

    /** Overrides the dimension reported by {@code getWorld} for one player. */
    public void setPlayerDimension(String playerHandle, PracticeDimension dimension) {
        if (dimension == null) {
            playerDimensions.remove(playerHandle);
        } else {
            playerDimensions.put(playerHandle, dimension);
        }
    }

    /** Makes {@code itemExists} return false for one id (compat tests). */
    public void forbidItem(String itemId) {
        missingItems.add(itemId);
    }

    /** Overrides {@code maxStackSize} for one id (compat tests). */
    public void setMaxStackSize(String itemId, int size) {
        stackSizes.put(itemId, size);
    }

    public void setSaturation(String playerHandle, float value) {
        saturation.put(playerHandle, value);
    }

    public float saturationOf(String playerHandle) {
        Float value = saturation.get(playerHandle);
        return value == null ? 5.0f : value;
    }

    public void setXp(String playerHandle, int level, int points) {
        xpLevels.put(playerHandle, level);
        xpPoints.put(playerHandle, points);
    }

    public int xpLevelOf(String playerHandle) {
        Integer value = xpLevels.get(playerHandle);
        return value == null ? 0 : value;
    }

    public void setEffects(String playerHandle, List<String> effectIds) {
        effects.put(playerHandle, new ArrayList<String>(effectIds));
    }

    public List<String> effectsOf(String playerHandle) {
        List<String> value = effects.get(playerHandle);
        return value == null ? Collections.<String>emptyList() : Collections.unmodifiableList(value);
    }

    public void setSelectedSlot(String playerHandle, int slot) {
        selectedSlots.put(playerHandle, slot);
    }

    public int selectedSlotOf(String playerHandle) {
        Integer value = selectedSlots.get(playerHandle);
        return value == null ? 0 : value;
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
                FakeWorld stored = worlds.get(world.handleId());
                if (stored == null) {
                    worlds.put(world.handleId(), new FakeWorld(world.handleId(), seed,
                            options.dimension(), version));
                } else {
                    stored.reset(seed, options.dimension());
                }
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
                saturation.put(player.handleId(), 5.0f);
                xpLevels.put(player.handleId(), 0);
                xpPoints.put(player.handleId(), 0);
                effects.put(player.handleId(), new ArrayList<String>());
                selectedSlots.put(player.handleId(), 0);
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
                PracticeDimension override = playerDimensions.get(player.handleId());
                PracticeWorld current = worlds.values().iterator().next();
                if (override == null || override == current.dimension()) {
                    return current;
                }
                return new FakeWorld(current.handleId(), current.seed(), override, current.version());
            }

            @Override
            public PracticeCheckpoint.PlayerSnapshot capturePlayerState(PracticePlayer player) {
                String handle = player.handleId();
                PracticePosition position = positions.get(handle);
                if (position == null) {
                    position = new PracticePosition(0.0, 64.0, 0.0);
                }
                Double hp = health.get(handle);
                Integer foodLevel = food.get(handle);
                Loadout current = inventories.get(handle);
                return new PracticeCheckpoint.PlayerSnapshot(position,
                        hp == null ? 20.0 : hp, foodLevel == null ? 20 : foodLevel,
                        saturationOf(handle), xpLevelOf(handle),
                        xpPoints.get(handle) == null ? 0 : xpPoints.get(handle),
                        current == null ? null : new Loadout("checkpoint", current.items()),
                        new ArrayList<String>(effectsOf(handle)), selectedSlotOf(handle));
            }

            @Override
            public void restorePlayerState(PracticePlayer player, PracticeCheckpoint.PlayerSnapshot snapshot) {
                String handle = player.handleId();
                positions.put(handle, snapshot.position());
                health.put(handle, snapshot.health());
                food.put(handle, snapshot.food());
                saturation.put(handle, snapshot.saturation());
                xpLevels.put(handle, snapshot.xpLevel());
                xpPoints.put(handle, snapshot.xpPoints());
                effects.put(handle, new ArrayList<String>(snapshot.effects()));
                selectedSlots.put(handle, snapshot.selectedSlot());
                if (snapshot.inventory() != null) {
                    inventories.put(handle, snapshot.inventory());
                } else {
                    inventories.remove(handle);
                }
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
                if (id == null) {
                    return null;
                }
                return id.contains(":") ? id : "minecraft:" + id;
            }

            @Override
            public boolean itemExists(String id) {
                return id != null && !missingItems.contains(id)
                        && !missingItems.contains(normalizeItemId(id));
            }

            @Override
            public int maxStackSize(String id) {
                Integer override = stackSizes.get(id);
                if (override == null) {
                    override = stackSizes.get(normalizeItemId(id));
                }
                return override == null ? 64 : override;
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
        private long seed;
        private PracticeDimension dimension;
        private final GameVersion version;

        public FakeWorld(String handleId, long seed, PracticeDimension dimension, GameVersion version) {
            this.handleId = handleId;
            this.seed = seed;
            this.dimension = dimension;
            this.version = version;
        }

        /** In-place reset, mirroring version adapters (stable handle, new seed). */
        void reset(long seed, PracticeDimension dimension) {
            this.seed = seed;
            this.dimension = dimension;
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
