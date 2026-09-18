package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.checkpoint.CheckpointManager;
import com.gregor0410.speedrunpractice.common.config.SpeedrunPracticeConfig;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutManager;
import com.gregor0410.speedrunpractice.common.scenario.ScenarioDefinition;
import com.gregor0410.speedrunpractice.common.scenario.ScenarioLoader;
import com.gregor0410.speedrunpractice.common.seeds.SeedStore;
import com.gregor0410.speedrunpractice.common.stats.FilePracticeStatistics;
import com.gregor0410.speedrunpractice.common.stats.PracticeStatistics;
import com.gregor0410.speedrunpractice.common.timer.MonotonicPracticeTimer;
import com.gregor0410.speedrunpractice.common.timer.PracticeTimer;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.nio.file.Path;
import java.util.Map;

/**
 * Builds the one shared {@link PracticeRuntime} for a version entrypoint
 * (plan section 5). Each Minecraft version creates exactly one runtime and
 * keeps it reachable from its own code; no global static state is used.
 *
 * <pre>
 * MinecraftAdapter adapter = new AdapterSet116(...);
 * PracticeRuntime runtime = SpeedrunPracticeBootstrap.create(adapter, configDir);
 * adapter.commands().register(PracticeCommands.buildTree(), runtime.asCommandExecutor());
 * </pre>
 */
public final class SpeedrunPracticeBootstrap {
    private SpeedrunPracticeBootstrap() {
    }

    /**
     * Creates a runtime for {@code adapter}, loading config, custom scenarios
     * and loadouts from {@code configDir} (missing or corrupt inputs fall back
     * to safe defaults with a warning, never a crash). Command registration
     * stays version-side: entrypoints register
     * {@code PracticeCommands.buildTree()} with {@code runtime.asCommandExecutor()}
     * once their dispatcher is ready.
     */
    public static PracticeRuntime create(MinecraftAdapter adapter, Path configDir) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        if (configDir == null) {
            throw new IllegalArgumentException("configDir must not be null");
        }
        SpeedrunPracticeConfig config = SpeedrunPracticeConfig.load(configDir);
        SeedStore seedStore = new SeedStore(configDir.resolve("seeds"));
        LoadoutManager loadouts = new LoadoutManager.InMemoryLoadoutManager();
        PracticeTimer timer = new MonotonicPracticeTimer();
        PracticeStatistics statistics = new FilePracticeStatistics(configDir.resolve("stats.json"));
        CheckpointManager checkpoints = new CheckpointManager.InMemoryCheckpointManager(adapter, timer);
        ScenarioEngine engine = new ScenarioEngine(adapter, checkpoints, timer, statistics, loadouts);
        Map<String, ScenarioDefinition> customs = ScenarioLoader.loadAll(configDir.resolve("scenarios"));
        PracticeRuntime runtime = new PracticeRuntime(adapter, engine, seedStore, loadouts, checkpoints,
                timer, statistics, config, configDir, customs);
        runtime.loadLoadoutsFromDisk();
        SpeedrunLogger.info("PracticeRuntime ready for " + adapter.version().versionString()
                + " (" + configDir + "): " + customs.size() + " custom scenarios, "
                + loadouts.list().size() + " loadouts");
        return runtime;
    }
}
