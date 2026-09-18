package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.CommandAdapter;
import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeResult;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.checkpoint.CheckpointManager;
import com.gregor0410.speedrunpractice.common.config.SpeedrunPracticeConfig;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutManager;
import com.gregor0410.speedrunpractice.common.scenario.ScenarioDefinition;
import com.gregor0410.speedrunpractice.common.scenario.ScenarioLoader;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.seeds.SeedRequest;
import com.gregor0410.speedrunpractice.common.seeds.SeedSearchTask;
import com.gregor0410.speedrunpractice.common.seeds.SeedSource;
import com.gregor0410.speedrunpractice.common.seeds.SeedSources;
import com.gregor0410.speedrunpractice.common.seeds.SeedStore;
import com.gregor0410.speedrunpractice.common.stats.PracticeStatistics;
import com.gregor0410.speedrunpractice.common.timer.PracticeTimer;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Central shared runtime owner (plan sections 4 and 5). One instance per
 * version entrypoint; owns the scenario engine plus configuration, scenarios,
 * loadouts, seeds, checkpoints, statistics and the timer. Version code
 * reaches game state only through the {@link MinecraftAdapter}; this class
 * never touches Minecraft types.
 *
 * <p>Prefer {@link SpeedrunPracticeBootstrap#create(MinecraftAdapter, Path)}
 * over calling this constructor directly.
 */
public final class PracticeRuntime {
    private final MinecraftAdapter adapter;
    private final ScenarioEngine engine;
    private final SeedStore seedStore;
    private final LoadoutManager loadouts;
    private final CheckpointManager checkpoints;
    private final PracticeTimer timer;
    private final PracticeStatistics statistics;
    private final Path configDir;
    private final Map<String, ScenarioDefinition> customScenarios =
            new LinkedHashMap<String, ScenarioDefinition>();
    private final CommandAdapter.CommandExecutor commandExecutor;
    private SpeedrunPracticeConfig config;
    private SeedSource activeSeedSource;
    private SeedSearchTask activeSearch;

    /**
     * Wires an already-built engine to its supporting systems. All arguments
     * are required except {@code customScenarios}, which defaults to empty.
     */
    public PracticeRuntime(MinecraftAdapter adapter, ScenarioEngine engine, SeedStore seedStore,
                           LoadoutManager loadouts, CheckpointManager checkpoints, PracticeTimer timer,
                           PracticeStatistics statistics, SpeedrunPracticeConfig config, Path configDir,
                           Map<String, ScenarioDefinition> customScenarios) {
        if (adapter == null || engine == null || seedStore == null || loadouts == null
                || checkpoints == null || timer == null || statistics == null
                || config == null || configDir == null) {
            throw new IllegalArgumentException("runtime collaborators must not be null");
        }
        this.adapter = adapter;
        this.engine = engine;
        this.seedStore = seedStore;
        this.loadouts = loadouts;
        this.checkpoints = checkpoints;
        this.timer = timer;
        this.statistics = statistics;
        this.config = config;
        this.configDir = configDir;
        if (customScenarios != null) {
            this.customScenarios.putAll(customScenarios);
        }
        this.commandExecutor = new PracticeActionExecutor(this);
    }

    public MinecraftAdapter adapter() {
        return adapter;
    }

    public ScenarioEngine engine() {
        return engine;
    }

    public SeedStore seedStore() {
        return seedStore;
    }

    public LoadoutManager loadouts() {
        return loadouts;
    }

    public CheckpointManager checkpoints() {
        return checkpoints;
    }

    public PracticeTimer timer() {
        return timer;
    }

    public PracticeStatistics statistics() {
        return statistics;
    }

    public SpeedrunPracticeConfig config() {
        return config;
    }

    public Path configDir() {
        return configDir;
    }

    /** Custom definitions loaded from {@code scenarios/}, by definition id. */
    public Map<String, ScenarioDefinition> customScenarios() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, ScenarioDefinition>(customScenarios));
    }

    public boolean hasActive() {
        return engine.hasActive();
    }

    /** Seed source used for new/previous-seed resets; null until first set. */
    public SeedSource activeSeedSource() {
        return activeSeedSource;
    }

    public void useSeedSource(SeedSource source) {
        if (source == null) {
            throw new IllegalArgumentException("seed source must not be null");
        }
        this.activeSeedSource = source;
    }

    /**
     * Starts a built-in practice, drawing its seed from the
     * {@code seed.source} setting (default {@code random}). The resolved
     * source becomes the active one, so new/previous-seed resets continue
     * from it. Fixed/imported sources read {@code seed.value}/{@code seed.list}.
     */
    public PracticeSession startPractice(PracticeType type, PracticeSettings settings, PracticePlayer player)
            throws PracticeException {
        PracticeSettings effective = effectiveSettings(settings);
        // Create the scenario before consuming a seed, so a bad type never
        // advances a list source.
        PracticeScenario scenario = scenarioFor(type);
        SeedSource source = resolveSeedSource(effective);
        validatePreset(scenario, effective);
        return launch(scenario, effective, player, drawSeed(source, effective.seedSource()));
    }

    /** Starts a built-in practice on one explicit seed. */
    public PracticeSession startPractice(PracticeType type, PracticeSettings settings,
                                         PracticePlayer player, long seed) throws PracticeException {
        PracticeSettings effective = effectiveSettings(settings);
        PracticeScenario scenario = scenarioFor(type);
        validatePreset(scenario, effective);
        ensureSeedSource();
        return launch(scenario, effective, player, seed);
    }

    /**
     * Starts a custom (JSON-defined) practice, drawing its seed from the
     * merged settings: explicit run settings win, the definition fills gaps
     * (including its {@code seed.source}/{@code seed.value}/{@code seed.list}).
     */
    public PracticeSession startCustom(String customId, PracticeSettings settings, PracticePlayer player)
            throws PracticeException {
        ScenarioDefinition definition = requireDefinition(customId);
        PracticeSettings effective = customSettings(definition, settings);
        SeedSource source = resolveSeedSource(effective);
        validatePreset(new CustomScenario(definition), effective);
        return launch(new CustomScenario(definition), effective, player,
                drawSeed(source, effective.seedSource()));
    }

    /** Starts a custom (JSON-defined) practice on one explicit seed. */
    public PracticeSession startCustom(String customId, PracticeSettings settings,
                                       PracticePlayer player, long seed) throws PracticeException {
        ScenarioDefinition definition = requireDefinition(customId);
        PracticeSettings effective = customSettings(definition, settings);
        validatePreset(new CustomScenario(definition), effective);
        ensureSeedSource();
        return launch(new CustomScenario(definition), effective, player, seed);
    }

    /**
     * Validates a practice preset before any game state is mutated (plan
     * section 87): the scenario must exist, the seed source must resolve
     * (checked by the caller via {@link #resolveSeedSource} or an explicit
     * seed), the loadout must exist, and custom definitions must have their
     * required capability available. Readable errors, never a half-built
     * world.
     */
    public void validatePreset(PracticeScenario scenario, PracticeSettings settings) throws PracticeException {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }
        PracticeSettings effective = effectiveSettings(settings);
        String loadoutId = effective.loadoutId();
        if (loadoutId != null && loadouts.get(loadoutId) == null) {
            throw new PracticeException("Unknown loadout: " + loadoutId,
                    "Loadout \"" + loadoutId + "\" does not exist. Pick one from /practice loadout list.");
        }
        if (scenario instanceof CustomScenario) {
            String required = ((CustomScenario) scenario).definition().requiresCapability();
            if (required != null
                    && !adapter.supports(
                            com.gregor0410.speedrunpractice.common.adapter.Capability.valueOf(required))) {
                throw new PracticeException("Custom scenario \"" + scenario.id().value()
                        + "\" needs unsupported capability " + required,
                        "This practice needs \"" + required + "\", which is not available on "
                                + adapter.version().versionString() + ".");
            }
        }
    }

    public PracticeScenario.TickResult tick() throws PracticeException {
        ensureActive();
        return engine.tickCurrent();
    }

    public void reset(PracticeScenario.ResetMode mode) throws PracticeException {
        ensureActive();
        if (mode == null) {
            throw new IllegalArgumentException("reset mode must not be null");
        }
        engine.resetCurrent(mode, activeSeedSource);
        if (mode == PracticeScenario.ResetMode.NEW_SEED || mode == PracticeScenario.ResetMode.FULL_RESET) {
            noteRecent(engine.currentContext().seed());
        }
    }

    /** Restarts the active practice on one explicit seed, keeping the active source. */
    public void restartOnSeed(long seed) throws PracticeException {
        ensureActive();
        engine.resetCurrent(PracticeScenario.ResetMode.FULL_RESET, new SeedSources.FixedSeedSource(seed));
        noteRecent(seed);
    }

    /** Stops the active practice; a no-op when idle. */
    public void stop() throws PracticeException {
        engine.stopCurrent();
    }

    public void saveCheckpoint() throws PracticeException {
        ensureActive();
        engine.saveCheckpointCurrent();
    }

    public void restoreCheckpoint() throws PracticeException {
        ensureActive();
        engine.resetCurrent(PracticeScenario.ResetMode.CHECKPOINT, activeSeedSource);
    }

    /** Clears the active practice's checkpoint; a no-op when idle. */
    public void clearCheckpoint() {
        engine.clearCheckpointCurrent();
    }

    public boolean hasCheckpoint() {
        return engine.hasCheckpointCurrent();
    }

    /** Empty when no practice is running. */
    public OptionalLong currentSeed() {
        if (!hasActive()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(engine.currentContext().seed());
    }

    /** Favorites the running seed; returns it. */
    public long favoriteCurrentSeed() throws PracticeException {
        ensureActive();
        long seed = engine.currentContext().seed();
        try {
            seedStore.addFavorite(seed);
        } catch (IOException failure) {
            throw new PracticeException("Cannot favorite seed " + seed + ": " + failure.getMessage(),
                    "Could not save seed " + seed + " to favorites: " + failure.getMessage());
        }
        return seed;
    }

    /**
     * Tracks a version-built search task (e.g. from the seed-search module),
     * cancelling any previous one. Thread ownership stays with the creator:
     * the runtime only cancels; the creator shuts down its own executor.
     */
    public void startSearch(SeedSearchTask task) {
        if (task == null) {
            throw new IllegalArgumentException("search task must not be null");
        }
        if (activeSearch != null) {
            activeSearch.cancel();
        }
        activeSearch = task;
    }

    /** Cancels the tracked search; false when none was running. */
    public boolean cancelSearch() {
        if (activeSearch == null) {
            return false;
        }
        activeSearch.cancel();
        activeSearch = null;
        return true;
    }

    /** The tracked search, or null when none was started. */
    public SeedSearchTask activeSearch() {
        return activeSearch;
    }

    /** Null when no search was started. */
    public SeedSearchTask.SearchProgress searchProgress() {
        return activeSearch == null ? null : activeSearch.progress();
    }

    /** Shared dispatcher for the {@code /practice} command tree. */
    public CommandAdapter.CommandExecutor asCommandExecutor() {
        return commandExecutor;
    }

    public void openMainMenu(PracticePlayer player) throws PracticeException {
        requireGui();
        adapter.gui().openMainMenu(player);
    }

    public void openScenarioScreen(PracticePlayer player, PracticePreset preset) throws PracticeException {
        requireGui();
        adapter.gui().openScenarioScreen(player, preset);
    }

    public void openResultsScreen(PracticePlayer player, PracticeResult result) throws PracticeException {
        requireGui();
        adapter.gui().openResultsScreen(player, result);
    }

    /**
     * Loads {@code loadouts/*.json} into the manager. Bad files are skipped
     * with a warning, never fatal, so a user typo cannot break startup.
     */
    public void loadLoadoutsFromDisk() {
        Path dir = loadoutsDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException failure) {
            SpeedrunLogger.warn("Cannot create loadout directory " + dir + ": " + failure.getMessage());
            return;
        }
        DirectoryStream<Path> stream = null;
        try {
            stream = Files.newDirectoryStream(dir, "*.json");
            for (Path file : stream) {
                try {
                    String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    loadouts.importLoadout(json);
                } catch (PracticeException bad) {
                    SpeedrunLogger.warn("Skipping " + file.getFileName() + ": " + bad.getUserMessage());
                } catch (IOException bad) {
                    SpeedrunLogger.warn("Skipping " + file.getFileName() + ": cannot read file");
                } catch (RuntimeException bad) {
                    SpeedrunLogger.warn("Skipping " + file.getFileName() + ": " + bad.getMessage());
                }
            }
        } catch (IOException failure) {
            SpeedrunLogger.warn("Cannot list loadout directory " + dir + ": " + failure.getMessage());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Best effort.
                }
            }
        }
    }

    /** Writes every known loadout to {@code loadouts/*.json}. */
    public void persistLoadouts() throws PracticeException {
        Path dir = loadoutsDir();
        try {
            Files.createDirectories(dir);
            for (Loadout loadout : loadouts.list()) {
                Path file = dir.resolve(safeName(loadout.id()) + ".json");
                Files.write(file, loadout.toJson(true).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException failure) {
            throw new PracticeException("Cannot save loadouts to " + dir + ": " + failure.getMessage(),
                    "Could not save loadouts: " + failure.getMessage());
        }
    }

    /**
     * Reloads every disk-backed input: config, custom scenarios, loadouts.
     * In-memory-only loadouts are replaced by what is on disk. Returns a
     * one-line summary for command feedback.
     */
    public String reload() {
        config = SpeedrunPracticeConfig.load(configDir);
        customScenarios.clear();
        customScenarios.putAll(ScenarioLoader.loadAll(configDir.resolve("scenarios")));
        List<String> ids = new ArrayList<String>();
        for (Loadout loadout : loadouts.list()) {
            ids.add(loadout.id());
        }
        for (String id : ids) {
            loadouts.delete(id);
        }
        loadLoadoutsFromDisk();
        String summary = "config, " + customScenarios.size() + " custom scenarios, "
                + loadouts.list().size() + " loadouts";
        SpeedrunLogger.info("Reloaded " + summary + " from " + configDir);
        return summary;
    }

    /** Stops practice and cancels search; never throws (cleanup path). */
    public void shutdown() {
        try {
            engine.stopCurrent();
        } catch (PracticeException failure) {
            SpeedrunLogger.warn("Error while stopping practice during shutdown: " + failure.getMessage());
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Error while stopping practice during shutdown: " + failure.getMessage());
        }
        cancelSearch();
    }

    private PracticeSession launch(PracticeScenario scenario, PracticeSettings settings,
                                   PracticePlayer player, long seed) throws PracticeException {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        PracticeSession session = engine.startScenario(scenario, settings, player, seed);
        noteRecent(seed);
        return session;
    }

    private PracticeScenario scenarioFor(PracticeType type) throws PracticeException {
        if (type == null) {
            throw new IllegalArgumentException("practice type must not be null");
        }
        return ScenarioRegistry.create(type);
    }

    private ScenarioDefinition requireDefinition(String customId) throws PracticeException {
        if (customId == null || customId.trim().isEmpty()) {
            throw new IllegalArgumentException("custom scenario id must not be empty");
        }
        ScenarioDefinition definition = customScenarios.get(customId.trim());
        if (definition == null) {
            throw new PracticeException("Unknown custom scenario \"" + customId + "\"",
                    "Custom scenario \"" + customId + "\" does not exist. Check the scenarios folder.");
        }
        return definition;
    }

    private PracticeSettings customSettings(ScenarioDefinition definition, PracticeSettings settings) {
        PracticeSettings effective = new PracticeSettings(settings == null ? null : settings.asMap());
        for (Map.Entry<String, String> entry : definition.toSettings().asMap().entrySet()) {
            if (effective.get(entry.getKey()) == null) {
                effective.set(entry.getKey(), entry.getValue());
            }
        }
        return effective;
    }

    private PracticeSettings effectiveSettings(PracticeSettings settings) {
        return settings == null ? new PracticeSettings() : settings;
    }

    private SeedSource resolveSeedSource(PracticeSettings settings) throws PracticeException {
        SeedSource source = SeedSources.forName(settings.seedSource(), seedStore, adapter.seeds(),
                queryFromSettings(settings));
        activeSeedSource = source;
        return source;
    }

    private void ensureSeedSource() {
        if (activeSeedSource == null) {
            activeSeedSource = new SeedSources.RandomSeedSource();
        }
    }

    private SeedQuery queryFromSettings(PracticeSettings settings) {
        SeedQuery.Builder builder = SeedQuery.builder().version(adapter.version());
        if (settings.get("seed.value") != null) {
            builder.constraint("seed.value", settings.get("seed.value"));
        }
        if (settings.get("seed.list") != null) {
            builder.constraint("seed.list", settings.get("seed.list"));
        }
        return builder.build();
    }

    private long drawSeed(SeedSource source, String sourceName) throws PracticeException {
        OptionalLong next = source.nextSeed(SeedRequest.any());
        if (!next.isPresent()) {
            throw new PracticeException.SeedSearchException(
                    "Seed source \"" + sourceName + "\" has no seed available",
                    "No seed available from \"" + sourceName + "\". "
                            + "Adjust the seed list or pick another source.");
        }
        return next.getAsLong();
    }

    private void noteRecent(long seed) {
        try {
            seedStore.addRecent(seed);
        } catch (IOException failure) {
            SpeedrunLogger.warn("Cannot record recent seed " + seed + ": " + failure.getMessage());
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Cannot record recent seed " + seed + ": " + failure.getMessage());
        }
    }

    private void ensureActive() throws PracticeException {
        if (!hasActive()) {
            throw new PracticeException("No active practice scenario",
                    "No practice is running. Start one with /practice start <type>.");
        }
    }

    private void requireGui() throws PracticeException {
        if (!adapter.gui().isAvailable()) {
            throw new PracticeException("GUI is not available on " + adapter.version().versionString(),
                    "The practice menu is not available here. Commands still work.");
        }
    }

    private Path loadoutsDir() {
        return configDir.resolve("loadouts");
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
