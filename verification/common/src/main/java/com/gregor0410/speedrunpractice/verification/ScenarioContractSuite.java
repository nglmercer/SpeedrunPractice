package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verification-only integration checks for the real scenario lifecycle.
 * This class deliberately depends on {@link PracticeRuntime}, not on a fake
 * Minecraft adapter: version-local probes supply a real live player handle.
 */
public final class ScenarioContractSuite {
    private static final long BURIED_TREASURE_SEED = 1L;

    private ScenarioContractSuite() {
    }

    public static Result run(PracticeRuntime runtime, PracticePlayer player, long seed) {
        Result result = new Result();
        if (runtime == null || player == null) {
            result.record("scenario.runtime", false, "runtime or player is null");
            return result;
        }

        runCase(result, runtime, player, seed, "scenario.overworld", PracticeType.OVERWORLD,
                new PracticeSettings(), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        runtime.fireEvent(new PracticeEvent.DimensionChangedEvent(
                                PracticeDimension.OVERWORLD, PracticeDimension.NETHER));
                    }
                });
        // Seed 12345 is the shared world/lifecycle seed, but it has no
        // buried-treasure start in 1.21.1. Use the live-analyzer candidate
        // so this scenario exercises the real locate-and-complete path on
        // every adapter instead of silently accepting a missing target.
        runCase(result, runtime, player, BURIED_TREASURE_SEED, "scenario.buried_treasure",
                PracticeType.BURIED_TREASURE,
                settings("buried_treasure.spawnOffset", "0",
                        "buried_treasure.completeRadius", "64"), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        runtime.tick();
                    }
                });
        runCase(result, runtime, player, seed, "scenario.nether", PracticeType.NETHER,
                new PracticeSettings(), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        runtime.fireEvent(new PracticeEvent.DimensionChangedEvent(
                                PracticeDimension.NETHER, PracticeDimension.OVERWORLD));
                    }
                });
        runCase(result, runtime, player, seed, "scenario.bastion", PracticeType.BASTION,
                settings("bastion.goal", "leave_bastion",
                        "spawn.mode", "entrance",
                        "bastion.completeRadius", "256"), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        runtime.tick();
                        moveFarAndTick(runtime, player);
                    }
                });
        runCase(result, runtime, player, seed, "scenario.fortress", PracticeType.FORTRESS,
                settings("fortress.mode", "exit",
                        "fortress.completeRadius", "256"), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        runtime.tick();
                        moveFarAndTick(runtime, player);
                    }
                });
        runCase(result, runtime, player, seed, "scenario.blind_travel", PracticeType.BLIND_TRAVEL,
                settings("blind.targetDistance", "0"), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        runtime.fireEvent(new PracticeEvent.PortalExitEvent(
                                PracticeDimension.OVERWORLD, new PracticePosition(0, 64, 0)));
                    }
                });
        runCase(result, runtime, player, seed, "scenario.postblind", PracticeType.POSTBLIND,
                settings("postblind.minDist", "0",
                        "postblind.maxDist", "0",
                        "postblind.searchRadius", "4096",
                        "postblind.completeRadius", "256"), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        runtime.tick();
                    }
                });
        runCase(result, runtime, player, seed, "scenario.stronghold", PracticeType.STRONGHOLD,
                settings("stronghold.mode", "entry",
                        "stronghold.goal", "reach_stronghold",
                        "stronghold.searchRadius", "4096",
                        "stronghold.completeRadius", "256"), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        runtime.tick();
                    }
                });
        runCase(result, runtime, player, seed, "scenario.end", PracticeType.END,
                new PracticeSettings(), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        fireDragonDeath(runtime);
                    }
                });
        runCase(result, runtime, player, seed, "scenario.onecycle", PracticeType.ONE_CYCLE,
                new PracticeSettings(), new Completion() {
                    @Override
                    public void run(PracticeRuntime runtime, PracticePlayer player) throws Exception {
                        fireDragonDeath(runtime);
                    }
                });

        runCustomCase(result, runtime, player, seed);
        return result;
    }

    private static void runCase(Result result, PracticeRuntime runtime, PracticePlayer player, long seed,
                                String name, PracticeType type, PracticeSettings settings,
                                Completion completion) {
        SpeedrunLogger.info("Verification " + name + " starting");
        boolean setup = false;
        boolean completed = false;
        boolean cleaned = false;
        String failureText = null;
        try {
            PracticeSession session = runtime.startPractice(type, settings, player, seed);
            ScenarioEngine engine = runtime.engine();
            setup = session.state() == PracticeState.RUNNING
                    && runtime.hasActive()
                    && engine.currentContext() != null
                    && engine.currentContext().world() != null;
            if (setup) {
                completion.run(runtime, player);
                completed = session.state() == PracticeState.COMPLETED;
            }
        } catch (Exception failure) {
            failureText = message(failure);
        } finally {
            try {
                if (runtime.hasActive()) {
                    runtime.stop();
                }
                cleaned = !runtime.hasActive();
            } catch (Exception failure) {
                failureText = message(failure);
            }
        }
        result.record(name, setup && completed && cleaned,
                failureText == null ? (setup + ",completed=" + completed + ",cleaned=" + cleaned)
                        : failureText);
        SpeedrunLogger.info("Verification " + name + " "
                + (setup && completed && cleaned ? "passed" : "failed")
                + (failureText == null ? "" : ": " + failureText));
    }

    private static void runCustomCase(Result result, PracticeRuntime runtime, PracticePlayer player, long seed) {
        SpeedrunLogger.info("Verification scenario.custom starting");
        Path scenarios = runtime.configDir().resolve("scenarios");
        Path file = null;
        String id = "verification_custom_" + runtime.adapter().version().versionString().replace('.', '_');
        try {
            Files.createDirectories(scenarios);
            file = scenarios.resolve(id + ".json");
            if (Files.exists(file)) {
                id = id + "_" + System.nanoTime();
                file = scenarios.resolve(id + ".json");
            }
            String json = "{\"id\":\"" + id + "\",\"type\":\"custom\","
                    + "\"world\":{\"dimension\":\"overworld\"},"
                    + "\"seed\":{\"source\":\"fixed\",\"value\":" + seed + "},"
                    + "\"spawn\":{\"type\":\"world_spawn\"},"
                    + "\"timer\":{\"start\":\"scenario_load\",\"stop\":\"scenario_complete\"},"
                    + "\"completion\":{\"type\":\"dimension_entry\",\"dimension\":\"overworld\"}}";
            Files.write(file, json.getBytes(StandardCharsets.UTF_8));
            runtime.reload();
            if (!runtime.customScenarios().containsKey(id)) {
                result.record("scenario.custom", false, "definition did not load");
                return;
            }
            boolean setup = false;
            boolean completed = false;
            boolean cleaned = false;
            String failureText = null;
            try {
                PracticeSession session = runtime.startCustom(id, new PracticeSettings(), player, seed);
                setup = session.state() == PracticeState.RUNNING && runtime.hasActive()
                        && runtime.engine().currentContext() != null
                        && runtime.engine().currentContext().world() != null;
                if (setup) {
                    runtime.tick();
                    completed = session.state() == PracticeState.COMPLETED;
                }
            } catch (Exception failure) {
                failureText = message(failure);
            } finally {
                try {
                    if (runtime.hasActive()) {
                        runtime.stop();
                    }
                    cleaned = !runtime.hasActive();
                } catch (Exception failure) {
                    failureText = message(failure);
                }
            }
            result.record("scenario.custom", setup && completed && cleaned,
                    failureText == null ? (setup + ",completed=" + completed + ",cleaned=" + cleaned)
                            : failureText);
            SpeedrunLogger.info("Verification scenario.custom "
                    + (setup && completed && cleaned ? "passed" : "failed")
                    + (failureText == null ? "" : ": " + failureText));
        } catch (Exception failure) {
            result.record("scenario.custom", false, message(failure));
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                    runtime.reload();
                } catch (Exception ignored) {
                    // The dedicated verification run prunes its private config directory.
                }
            }
        }
    }

    private static void moveFarAndTick(PracticeRuntime runtime, PracticePlayer player) throws Exception {
        PracticeWorld world = runtime.engine().currentContext().world();
        PracticePosition spawn = runtime.adapter().worlds().spawnPosition(world);
        // Keep the live smoke check outside the 256-block completion radius
        // without forcing the server to generate a distant view-distance
        // region (10,000 blocks made 1.21.1 retain excessive chunk work).
        runtime.adapter().players().teleport(player,
                new PracticePosition(spawn.x() + 300.0, spawn.y(), spawn.z() + 300.0));
        runtime.tick();
    }

    private static void fireDragonDeath(PracticeRuntime runtime) throws Exception {
        PracticeWorld world = runtime.engine().currentContext().world();
        runtime.fireEvent(new PracticeEvent.DragonKilledEvent(world.handleId()));
    }

    private static PracticeSettings settings(String... values) {
        PracticeSettings settings = new PracticeSettings();
        for (int i = 0; i + 1 < values.length; i += 2) {
            settings.set(values[i], values[i + 1]);
        }
        return settings;
    }

    private static String message(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + ": "
                + (message == null ? "no message" : message.replace('\n', ' ').replace(',', ';'));
    }

    private interface Completion {
        void run(PracticeRuntime runtime, PracticePlayer player) throws Exception;
    }

    public static final class Result {
        private int matches;
        private int total;
        private final List<String> evidence = new ArrayList<String>();

        private void record(String name, boolean passed, String detail) {
            total++;
            if (passed) {
                matches++;
                evidence.add(name);
            } else {
                evidence.add(name + "=FAILED{" + detail + "}");
            }
        }

        public int matches() {
            return matches;
        }

        public int total() {
            return total;
        }

        public List<String> evidence() {
            return new ArrayList<String>(evidence);
        }
    }
}
