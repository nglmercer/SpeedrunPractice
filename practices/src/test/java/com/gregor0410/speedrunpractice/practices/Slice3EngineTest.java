package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.checkpoint.CheckpointManager;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutManager;
import com.gregor0410.speedrunpractice.common.scenario.ScenarioDefinition;
import com.gregor0410.speedrunpractice.common.scenario.ScenarioLoader;
import com.gregor0410.speedrunpractice.common.seeds.SeedSources;
import com.gregor0410.speedrunpractice.common.stats.InMemoryPracticeStatistics;
import com.gregor0410.speedrunpractice.common.stats.PracticeStatistics;
import com.gregor0410.speedrunpractice.common.timer.MonotonicPracticeTimer;
import com.gregor0410.speedrunpractice.common.timer.PracticeTimer;
import com.gregor0410.speedrunpractice.testsupport.ScenarioTestHarness;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Slice 3 engine coverage: timer start/stop conditions, event dispatch,
 * exception-safe start/reset (FAILED + cleanup), the STOPPING state,
 * preset validation, checkpoint world consistency + scenario state, full
 * player snapshots, stats persistence and loadout compatibility.
 */
public class Slice3EngineTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final class EngineBundle {
        final ScenarioTestHarness harness = new ScenarioTestHarness();
        final PracticeTimer timer = new MonotonicPracticeTimer();
        final PracticeStatistics stats = new InMemoryPracticeStatistics();
        final ScenarioEngine engine;

        EngineBundle() {
            LoadoutManager loadouts = new LoadoutManager.InMemoryLoadoutManager();
            loadouts.save(new Loadout("overworld_default", Collections.<Loadout.Item>emptyList()));
            CheckpointManager checkpoints =
                    new CheckpointManager.InMemoryCheckpointManager(harness, timer);
            engine = new ScenarioEngine(harness, checkpoints, timer, stats, loadouts);
        }
    }

    private static PracticeRuntime runtime(Path configDir, MinecraftAdapter adapter) {
        return SpeedrunPracticeBootstrap.create(adapter, configDir);
    }

    // Timer start conditions ---------------------------------------------------

    @Test
    public void timerStartsImmediatelyByDefault() throws Exception {
        EngineBundle bundle = new EngineBundle();
        bundle.engine.startScenario(new OverworldScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 1L);
        assertTrue(bundle.timer.isRunning());
        assertEquals(PracticeTimer.StartCondition.SCENARIO_LOAD, bundle.timer.getStartCondition());
        bundle.engine.stopCurrent();
    }

    @Test
    public void manualTimerWaitsForEvent() throws Exception {
        EngineBundle bundle = new EngineBundle();
        PracticeSettings settings = new PracticeSettings();
        settings.set("timer.start", "manual");
        bundle.engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        assertFalse(bundle.timer.isRunning());
        bundle.engine.fireEvent(new PracticeEvent.ManualTimerStartEvent());
        assertTrue(bundle.timer.isRunning());
        bundle.engine.stopCurrent();
    }

    @Test
    public void playerMoveStartsTimer() throws Exception {
        EngineBundle bundle = new EngineBundle();
        PracticeSettings settings = new PracticeSettings();
        settings.set("timer.start", "player_move");
        bundle.engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        assertFalse(bundle.timer.isRunning());
        bundle.engine.fireEvent(new PracticeEvent.PlayerMovedEvent(null,
                new PracticePosition(1.0, 64.0, 0.0)));
        assertTrue(bundle.timer.isRunning());
        bundle.engine.stopCurrent();
    }

    @Test
    public void dimensionEntryAndPortalExitStartTimer() throws Exception {
        EngineBundle bundle = new EngineBundle();
        PracticeSettings settings = new PracticeSettings();
        settings.set("timer.start", "dimension_entry");
        settings.set("overworld.goal", "manual");
        bundle.engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        assertFalse(bundle.timer.isRunning());
        bundle.engine.fireEvent(new PracticeEvent.DimensionChangedEvent(
                PracticeDimension.OVERWORLD, PracticeDimension.NETHER));
        assertTrue(bundle.timer.isRunning());
        bundle.engine.stopCurrent();

        EngineBundle portals = new EngineBundle();
        PracticeSettings portalSettings = new PracticeSettings();
        portalSettings.set("timer.start", "portal_exit");
        portals.engine.startScenario(new NetherScenario(), portalSettings,
                ScenarioTestHarness.player("p1"), 1L);
        assertFalse(portals.timer.isRunning());
        portals.engine.fireEvent(new PracticeEvent.PortalExitEvent(
                PracticeDimension.OVERWORLD, new PracticePosition(10.0, 70.0, 10.0)));
        assertTrue(portals.timer.isRunning());
        portals.engine.stopCurrent();
    }

    @Test
    public void unknownTimerStartFallsBackToScenarioLoad() throws Exception {
        EngineBundle bundle = new EngineBundle();
        PracticeSettings settings = new PracticeSettings();
        settings.set("timer.start", "warp_drive");
        bundle.engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        assertTrue(bundle.timer.isRunning());
        bundle.engine.stopCurrent();
    }

    @Test
    public void timerStartReappliesOnReset() throws Exception {
        EngineBundle bundle = new EngineBundle();
        PracticeSettings settings = new PracticeSettings();
        settings.set("timer.start", "manual");
        bundle.engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        bundle.engine.fireEvent(new PracticeEvent.ManualTimerStartEvent());
        assertTrue(bundle.timer.isRunning());
        bundle.engine.resetCurrent(PracticeScenario.ResetMode.SAME_SEED, null);
        assertFalse("reset must re-arm a manual timer", bundle.timer.isRunning());
        bundle.engine.stopCurrent();
    }

    // Timer stop conditions ----------------------------------------------------

    @Test
    public void dimensionEntryStopCompletes() throws Exception {
        EngineBundle bundle = new EngineBundle();
        PracticeSettings settings = new PracticeSettings();
        settings.set("overworld.goal", "manual");
        settings.set("timer.stop", "dimension_entry");
        bundle.engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        PracticeEvent event = new PracticeEvent.DimensionChangedEvent(
                PracticeDimension.OVERWORLD, PracticeDimension.NETHER);
        assertTrue(bundle.engine.fireEvent(event).isFinished());
        assertEquals(PracticeState.COMPLETED, bundle.engine.currentContext().session().state());
        assertEquals(1, bundle.stats.completed(PracticeId.of("overworld")));
        bundle.engine.stopCurrent();
    }

    @Test
    public void manualStopCompletes() throws Exception {
        EngineBundle bundle = new EngineBundle();
        PracticeSettings settings = new PracticeSettings();
        settings.set("overworld.goal", "manual");
        settings.set("timer.stop", "manual");
        bundle.engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        assertTrue(bundle.engine.fireEvent(new PracticeEvent.ManualTimerStopEvent()).isFinished());
        assertEquals(1, bundle.stats.completed(PracticeId.of("overworld")));
        bundle.engine.stopCurrent();
    }

    @Test
    public void structureReachedStopCompletes() throws Exception {
        EngineBundle bundle = new EngineBundle();
        PracticeSettings settings = new PracticeSettings();
        settings.set("overworld.goal", "manual");
        settings.set("timer.stop", "structure_reached");
        bundle.engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        assertTrue(bundle.engine.fireEvent(new PracticeEvent.StructureEnteredEvent(
                "village", new PracticePosition(300.0, 64.0, 300.0))).isFinished());
        bundle.engine.stopCurrent();
    }

    @Test
    public void dragonDeathStopIgnoresForeignWorlds() throws Exception {
        EngineBundle bundle = new EngineBundle();
        PracticeSettings settings = new PracticeSettings();
        settings.set("timer.stop", "dragon_death");
        bundle.engine.startScenario(new EndScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        String own = bundle.engine.currentContext().world().handleId();
        assertFalse(bundle.engine.fireEvent(new PracticeEvent.DragonKilledEvent("other-world"))
                .isFinished());
        assertEquals(PracticeState.RUNNING, bundle.engine.currentContext().session().state());
        assertTrue(bundle.engine.fireEvent(new PracticeEvent.DragonKilledEvent(own)).isFinished());
        bundle.engine.stopCurrent();
    }

    @Test
    public void completionFiresExactlyOnce() throws Exception {
        EngineBundle bundle = new EngineBundle();
        bundle.engine.startScenario(new EndScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 1L);
        // The fresh fight's dragon is alive first; only its death finishes.
        assertFalse(bundle.engine.tickCurrent().isFinished());
        bundle.harness.setLivingDragon(bundle.engine.currentContext().world().handleId(), false);
        assertTrue(bundle.engine.tickCurrent().isFinished());
        assertFalse(bundle.engine.tickCurrent().isFinished());
        assertFalse(bundle.engine.fireEvent(new PracticeEvent.DragonKilledEvent(
                bundle.engine.currentContext().world().handleId())).isFinished());
        assertEquals(1, bundle.stats.completed(PracticeId.of("end")));
        bundle.engine.stopCurrent();
    }

    // Event dispatch edges -----------------------------------------------------

    @Test
    public void fireEventIsLenientWhenIdle() throws Exception {
        EngineBundle bundle = new EngineBundle();
        assertFalse(bundle.engine.fireEvent(new PracticeEvent.ManualTimerStartEvent()).isFinished());
        bundle.engine.startScenario(new OverworldScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 1L);
        bundle.engine.stopCurrent();
        assertFalse(bundle.engine.fireEvent(new PracticeEvent.ManualTimerStopEvent()).isFinished());
    }

    @Test
    public void fireEventRejectsNull() throws Exception {
        EngineBundle bundle = new EngineBundle();
        bundle.engine.startScenario(new OverworldScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 1L);
        try {
            bundle.engine.fireEvent(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        bundle.engine.stopCurrent();
    }

    // Exception-safe start/reset -------------------------------------------------

    @Test
    public void failedStartCleansUpWorldAndRecordsAttempt() {
        EngineBundle bundle = new EngineBundle();
        try {
            bundle.engine.startScenario(new BastionScenario(), new PracticeSettings(),
                    ScenarioTestHarness.player("p1"), 7L);
            fail("expected PracticeException when no bastion is in range");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("Bastion"));
        }
        assertFalse(bundle.engine.hasActive());
        assertEquals(0, bundle.harness.worldCount());
        assertEquals(1, bundle.stats.attempts(PracticeId.of("bastion")));
        assertEquals(0, bundle.stats.completed(PracticeId.of("bastion")));
    }

    @Test
    public void runtimeExceptionsBecomePracticeExceptions() {
        EngineBundle bundle = new EngineBundle();
        PracticeScenario broken = new PracticeScenario() {
            @Override
            public PracticeId id() {
                return PracticeId.of("broken");
            }

            @Override
            public com.gregor0410.speedrunpractice.common.api.PracticeType type() {
                return com.gregor0410.speedrunpractice.common.api.PracticeType.OVERWORLD;
            }

            @Override
            public void prepare(PracticeContext context) {
            }

            @Override
            public void start(PracticeContext context) {
                throw new IllegalStateException("boom");
            }

            @Override
            public TickResult tick(PracticeContext context) {
                return TickResult.continueTick();
            }

            @Override
            public void reset(PracticeContext context, ResetMode mode) {
            }

            @Override
            public void stop(PracticeContext context) {
            }
        };
        try {
            bundle.engine.startScenario(broken, new PracticeSettings(), ScenarioTestHarness.player("p1"), 1L);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("broken"));
            assertNotNull(expected.getCause());
        }
        assertFalse(bundle.engine.hasActive());
    }

    @Test
    public void failedResetMarksFailedAndRecovers() throws Exception {
        final ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeTimer timer = new MonotonicPracticeTimer();
        PracticeStatistics stats = new InMemoryPracticeStatistics();
        CheckpointManager checkpoints = new CheckpointManager.InMemoryCheckpointManager(harness, timer);
        LoadoutManager loadouts = new LoadoutManager.InMemoryLoadoutManager();
        ScenarioEngine engine = new ScenarioEngine(harness, checkpoints, timer, stats, loadouts);
        PracticeScenario fragile = new OverworldScenario() {
            @Override
            public void reset(PracticeContext context, ResetMode mode) throws PracticeException {
                throw new PracticeException("reset boom", "Could not reset.");
            }
        };
        engine.startScenario(fragile, new PracticeSettings(), ScenarioTestHarness.player("p1"), 1L);
        assertEquals(1, harness.worldCount());
        try {
            engine.resetCurrent(PracticeScenario.ResetMode.SAME_SEED, null);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("Could not reset"));
        }
        assertEquals(PracticeState.FAILED, engine.currentContext().session().state());
        assertEquals(0, harness.worldCount());
        assertFalse(engine.tickCurrent().isFinished());
        engine.stopCurrent();
        assertFalse(engine.hasActive());
        engine.startScenario(new OverworldScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 2L);
        assertEquals(PracticeState.RUNNING, engine.currentContext().session().state());
        engine.stopCurrent();
    }

    @Test
    public void stopPassesThroughStopping() throws Exception {
        final List<PracticeState> observed = new ArrayList<PracticeState>();
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeTimer timer = new MonotonicPracticeTimer();
        CheckpointManager checkpoints = new CheckpointManager.InMemoryCheckpointManager(harness, timer);
        ScenarioEngine engine = new ScenarioEngine(harness, checkpoints, timer,
                new InMemoryPracticeStatistics(), new LoadoutManager.InMemoryLoadoutManager());
        PracticeScenario probe = new OverworldScenario() {
            @Override
            public void stop(PracticeContext context) {
                observed.add(context.session().state());
                super.stop(context);
            }
        };
        engine.startScenario(probe, new PracticeSettings(), ScenarioTestHarness.player("p1"), 1L);
        engine.stopCurrent();
        assertEquals(Collections.singletonList(PracticeState.STOPPING), observed);
    }

    // Preset validation ----------------------------------------------------------

    @Test
    public void unknownLoadoutFailsBeforeWorldCreation() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("loadout", "missing_pack");
        try {
            runtime.startPractice(PracticeType.OVERWORLD, settings, ScenarioTestHarness.player("p1"), 5L);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("missing_pack"));
        }
        assertFalse(runtime.hasActive());
        assertEquals(0, harness.worldCount());
    }

    @Test
    public void customRequiresEnforcedBeforeWorldCreation() throws Exception {
        Path root = folder.getRoot().toPath();
        final ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioDefinition definition = ScenarioLoader.loadFromJson(
                "{\"id\": \"needs_perch\", \"type\": \"end\", \"requires\": \"dragon_force_perch\"}", "test");
        java.nio.file.Files.createDirectories(root.resolve("scenarios"));
        java.nio.file.Files.write(root.resolve("scenarios").resolve("needs_perch.json"),
                "{\"id\": \"needs_perch\", \"type\": \"end\", \"requires\": \"dragon_force_perch\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MinecraftAdapter unsupporting = (MinecraftAdapter) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { MinecraftAdapter.class },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("supports")) {
                            return false;
                        }
                        return method.invoke(harness, args);
                    }
                });
        PracticeRuntime runtime = runtime(root, unsupporting);
        try {
            runtime.startCustom("needs_perch", new PracticeSettings(), ScenarioTestHarness.player("p1"), 5L);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("DRAGON_FORCE_PERCH"));
        }
        assertEquals(0, harness.worldCount());

        // The engine-level check agrees when called directly (same harness: the
        // proxy delegates world calls to it, and nothing was created).
        try {
            new ScenarioEngine(unsupporting,
                    new CheckpointManager.InMemoryCheckpointManager(unsupporting, new MonotonicPracticeTimer()),
                    new MonotonicPracticeTimer(), new InMemoryPracticeStatistics(),
                    new LoadoutManager.InMemoryLoadoutManager())
                    .startScenario(new CustomScenario(definition), new PracticeSettings(),
                            ScenarioTestHarness.player("p1"), 5L);
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("DRAGON_FORCE_PERCH"));
        }
        assertEquals(0, harness.worldCount());
    }

    // Checkpoints ------------------------------------------------------------------

    @Test
    public void checkpointRestoresAcrossSeedChange() throws Exception {
        EngineBundle bundle = new EngineBundle();
        bundle.engine.startScenario(new OverworldScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 11L);
        bundle.engine.saveCheckpointCurrent();
        bundle.engine.resetCurrent(PracticeScenario.ResetMode.NEW_SEED,
                new SeedSources.SeedListSource(Arrays.asList(22L)));
        assertEquals(22L, bundle.engine.currentContext().seed());
        assertEquals(22L, bundle.engine.currentContext().world().seed());
        bundle.harness.players().teleport(ScenarioTestHarness.player("p1"),
                new PracticePosition(999.0, 70.0, 999.0));
        bundle.engine.resetCurrent(PracticeScenario.ResetMode.CHECKPOINT, null);
        assertEquals(11L, bundle.engine.currentContext().seed());
        assertEquals(11L, bundle.engine.currentContext().world().seed());
        assertEquals(0.0, bundle.harness.positionOf("p1").x(), 0.0);
        bundle.engine.stopCurrent();
    }

    @Test
    public void fullPlayerSnapshotRoundTrip() throws Exception {
        EngineBundle bundle = new EngineBundle();
        ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("p1");
        bundle.engine.startScenario(new OverworldScenario(), new PracticeSettings(), player, 3L);
        bundle.harness.setSaturation("p1", 12.5f);
        bundle.harness.setXp("p1", 7, 30);
        bundle.harness.setEffects("p1", Arrays.asList("minecraft:speed"));
        bundle.harness.setSelectedSlot("p1", 5);
        bundle.engine.saveCheckpointCurrent();

        bundle.harness.setSaturation("p1", 0.0f);
        bundle.harness.setXp("p1", 0, 0);
        bundle.harness.setEffects("p1", Collections.<String>emptyList());
        bundle.harness.setSelectedSlot("p1", 0);
        bundle.engine.resetCurrent(PracticeScenario.ResetMode.CHECKPOINT, null);

        assertEquals(12.5f, bundle.harness.saturationOf("p1"), 0.0f);
        assertEquals(7, bundle.harness.xpLevelOf("p1"));
        assertEquals(Collections.singletonList("minecraft:speed"), bundle.harness.effectsOf("p1"));
        assertEquals(5, bundle.harness.selectedSlotOf("p1"));
        bundle.engine.stopCurrent();
    }

    @Test
    public void scenarioStateSurvivesCheckpoint() throws Exception {
        EngineBundle bundle = new EngineBundle();
        bundle.harness.scriptStructure("bastion_remnant", new PracticePosition(100.0, 70.0, 200.0),
                Collections.<String, String>emptyMap());
        PracticeSettings settings = new PracticeSettings();
        settings.set("bastion.goal", "leave_bastion");
        settings.set("spawn.mode", "outside");
        settings.set("spawn.distance", "40");
        ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("p1");
        bundle.engine.startScenario(new BastionScenario(), settings, player, 7L);

        // Walk in (marks entered), checkpoint, walk out, restore: the entered
        // flag and target come back, so walking out again still finishes.
        bundle.harness.players().teleport(player, new PracticePosition(100.0, 70.0, 200.0));
        assertFalse(bundle.engine.tickCurrent().isFinished());
        PracticeCheckpoint.ScenarioSnapshot snapshot = bundle.engine.currentScenario()
                .captureState(bundle.engine.currentContext());
        assertEquals("100.0,70.0,200.0", snapshot.data().get("target"));
        assertEquals("true", snapshot.data().get("entered"));
        bundle.engine.saveCheckpointCurrent();

        bundle.harness.players().teleport(player, new PracticePosition(0.0, 64.0, 0.0));
        bundle.engine.resetCurrent(PracticeScenario.ResetMode.CHECKPOINT, null);
        assertEquals(100.0, bundle.harness.positionOf("p1").x(), 0.0);
        bundle.harness.players().teleport(player, new PracticePosition(0.0, 64.0, 0.0));
        assertTrue(bundle.engine.tickCurrent().isFinished());
        bundle.engine.stopCurrent();
    }

    @Test
    public void corruptScenarioSnapshotRestoresLeniently() throws Exception {
        EngineBundle bundle = new EngineBundle();
        bundle.engine.startScenario(new OverworldScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 1L);
        java.util.Map<String, String> bad = new java.util.HashMap<String, String>();
        bad.put("target", "not,a,position,at,all,extra");
        bundle.engine.currentScenario().restoreState(bundle.engine.currentContext(),
                new PracticeCheckpoint.MapScenarioSnapshot("overworld", bad));
        bundle.engine.currentScenario().restoreState(bundle.engine.currentContext(), null);
        assertFalse(bundle.engine.tickCurrent().isFinished());
        bundle.engine.stopCurrent();
    }

    // Stats persistence + loadout compatibility ----------------------------------------

    @Test
    public void statisticsSurviveRuntimeRestart() throws Exception {
        Path root = folder.getRoot().toPath();
        PracticeRuntime first = runtime(root, new ScenarioTestHarness());
        first.startPractice(PracticeType.END, new PracticeSettings(), ScenarioTestHarness.player("p1"), 42L);
        first.stop();
        assertEquals(1, first.statistics().attempts(PracticeId.of("end")));
        assertTrue(java.nio.file.Files.exists(root.resolve("stats.json")));

        PracticeRuntime second = runtime(root, new ScenarioTestHarness());
        assertEquals(1, second.statistics().attempts(PracticeId.of("end")));
    }

    @Test
    public void unknownLoadoutItemsDegradeInsteadOfCrashing() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.forbidItem("minecraft:removed_item");
        harness.setMaxStackSize("minecraft:ender_pearl", 16);
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), harness);
        runtime.loadouts().save(new Loadout("mixed", Arrays.asList(
                new Loadout.Item("minecraft:stone", 0, 64),
                new Loadout.Item("minecraft:removed_item", 1, 1),
                new Loadout.Item("minecraft:ender_pearl", 2, 64))));
        PracticeSettings settings = new PracticeSettings();
        settings.set("loadout", "mixed");
        runtime.startPractice(PracticeType.OVERWORLD, settings, ScenarioTestHarness.player("p1"), 5L);
        Loadout applied = harness.appliedLoadout("p1");
        assertNotNull(applied);
        assertEquals(2, applied.items().size());
        assertEquals("minecraft:stone", applied.items().get(0).itemId());
        assertEquals(16, applied.items().get(1).count());
        runtime.stop();
    }
}
