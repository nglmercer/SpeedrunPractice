package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.checkpoint.CheckpointManager;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutManager;
import com.gregor0410.speedrunpractice.common.seeds.SeedSources;
import com.gregor0410.speedrunpractice.common.stats.InMemoryPracticeStatistics;
import com.gregor0410.speedrunpractice.common.stats.PracticeStatistics;
import com.gregor0410.speedrunpractice.common.timer.MonotonicPracticeTimer;
import com.gregor0410.speedrunpractice.common.timer.PracticeTimer;
import com.gregor0410.speedrunpractice.testsupport.ScenarioTestHarness;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScenarioEngineTest {
    private static ScenarioTestHarness harness() {
        return new ScenarioTestHarness();
    }

    private static ScenarioEngine engine(ScenarioTestHarness harness, PracticeTimer timer, PracticeStatistics stats) {
        LoadoutManager loadouts = new LoadoutManager.InMemoryLoadoutManager();
        loadouts.save(new Loadout("overworld_default", Collections.<Loadout.Item>emptyList()));
        CheckpointManager checkpoints = new CheckpointManager.InMemoryCheckpointManager(harness, timer);
        return new ScenarioEngine(harness, checkpoints, timer, stats, loadouts);
    }

    @Test
    public void startTickStopOverworld() throws Exception {
        ScenarioTestHarness harness = harness();
        PracticeTimer timer = new MonotonicPracticeTimer();
        PracticeStatistics stats = new InMemoryPracticeStatistics();
        ScenarioEngine engine = engine(harness, timer, stats);
        ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("p1");

        PracticeSession session = engine.startScenario(new OverworldScenario(), new PracticeSettings(), player, 42L);
        assertEquals(PracticeState.RUNNING, session.state());
        assertEquals(42L, session.seed());
        assertNotNull(engine.currentContext().world());
        assertEquals(0.0, harness.positionOf("p1").x(), 0.0);
        assertEquals(64.0, harness.positionOf("p1").y(), 0.0);
        assertFalse(engine.tickCurrent().isFinished());

        engine.stopCurrent();
        assertFalse(engine.hasActive());
        assertEquals(1, stats.attempts(PracticeId.of("overworld")));
    }

    @Test
    public void registryCreatesEveryNonCustomType() throws Exception {
        for (PracticeType type : ScenarioRegistry.types()) {
            if (type == PracticeType.CUSTOM) {
                continue;
            }
            assertNotNull(ScenarioRegistry.create(type));
        }
    }

    @Test
    public void bastionStartsOutsideMatchingType() throws Exception {
        ScenarioTestHarness harness = harness();
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(StructureAdapter.StructureLocation.BASTION_TYPE_KEY, "housing");
        harness.scriptStructure("bastion_remnant", new PracticePosition(100.0, 70.0, 200.0), metadata);
        PracticeTimer timer = new MonotonicPracticeTimer();
        ScenarioEngine engine = engine(harness, timer, new InMemoryPracticeStatistics());

        PracticeSettings settings = new PracticeSettings();
        settings.set("bastion.type", "housing");
        settings.set("spawn.distance", "40");
        engine.startScenario(new BastionScenario(), settings, ScenarioTestHarness.player("p1"), 7L);
        assertEquals(140.0, harness.positionOf("p1").x(), 0.0);
        assertEquals(200.0, harness.positionOf("p1").z(), 0.0);
        engine.stopCurrent();
    }

    @Test
    public void endCompletesWhenDragonDies() throws Exception {
        ScenarioTestHarness harness = harness();
        PracticeTimer timer = new MonotonicPracticeTimer();
        PracticeStatistics stats = new InMemoryPracticeStatistics();
        ScenarioEngine engine = engine(harness, timer, stats);

        engine.startScenario(new EndScenario(), new PracticeSettings(), ScenarioTestHarness.player("p1"), 3L);
        PracticeContext context = engine.currentContext();
        assertEquals(100.0, harness.positionOf("p1").x(), 0.0);
        assertFalse(engine.tickCurrent().isFinished());
        harness.setLivingDragon(context.world().handleId(), false);
        assertTrue(engine.tickCurrent().isFinished());
        assertEquals(1, stats.completed(PracticeId.of("end")));
        assertTrue(stats.personalBest(PracticeId.of("end")).isPresent());
        engine.stopCurrent();
    }

    @Test
    public void endDoesNotFinishBeforeDragonSpawns() throws Exception {
        ScenarioTestHarness harness = harness();
        ScenarioEngine engine = engine(harness, new MonotonicPracticeTimer(),
                new InMemoryPracticeStatistics());
        engine.startScenario(new EndScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 3L);
        PracticeContext context = engine.currentContext();
        // The fresh fight has no dragon yet: the attempt must not complete.
        harness.setLivingDragon(context.world().handleId(), false);
        assertFalse(engine.tickCurrent().isFinished());
        // A dragon that lived and died finishes.
        harness.setLivingDragon(context.world().handleId(), true);
        assertFalse(engine.tickCurrent().isFinished());
        harness.setLivingDragon(context.world().handleId(), false);
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void resetModesMoveSeeds() throws Exception {
        ScenarioTestHarness harness = harness();
        ScenarioEngine engine = engine(harness, new MonotonicPracticeTimer(), new InMemoryPracticeStatistics());
        engine.startScenario(new OverworldScenario(), new PracticeSettings(), ScenarioTestHarness.player("p1"), 1L);

        engine.resetCurrent(PracticeScenario.ResetMode.SAME_SEED, null);
        assertEquals(1L, engine.currentContext().seed());

        SeedSources.SeedListSource source = new SeedSources.SeedListSource(Arrays.asList(5L, 6L));
        engine.resetCurrent(PracticeScenario.ResetMode.NEW_SEED, source);
        assertEquals(5L, engine.currentContext().seed());
        engine.resetCurrent(PracticeScenario.ResetMode.NEW_SEED, source);
        assertEquals(6L, engine.currentContext().seed());
        engine.resetCurrent(PracticeScenario.ResetMode.PREVIOUS_SEED, source);
        assertEquals(5L, engine.currentContext().seed());
        engine.stopCurrent();
    }

    @Test
    public void checkpointResetRestoresPosition() throws Exception {
        ScenarioTestHarness harness = harness();
        ScenarioEngine engine = engine(harness, new MonotonicPracticeTimer(), new InMemoryPracticeStatistics());
        ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("p1");
        engine.startScenario(new OverworldScenario(), new PracticeSettings(), player, 11L);
        engine.saveCheckpointCurrent();
        assertTrue(engine.hasCheckpointCurrent());
        harness.players().teleport(player, new PracticePosition(999.0, 70.0, 999.0));
        engine.resetCurrent(PracticeScenario.ResetMode.CHECKPOINT, null);
        assertEquals(0.0, harness.positionOf("p1").x(), 0.0);
        engine.stopCurrent();
    }

    @Test
    public void repeatedLifecycleCyclesDoNotLeakPracticeWorlds() throws Exception {
        ScenarioTestHarness harness = harness();
        ScenarioEngine engine = engine(harness, new MonotonicPracticeTimer(),
                new InMemoryPracticeStatistics());
        ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("cycle-player");

        for (int cycle = 0; cycle < 50; cycle++) {
            engine.startScenario(new OverworldScenario(), new PracticeSettings(), player, cycle);
            assertEquals(cycle, engine.currentContext().seed());
            engine.resetCurrent(PracticeScenario.ResetMode.SAME_SEED, null);
            assertEquals(cycle, engine.currentContext().world().seed());
            engine.stopCurrent();
            assertEquals("practice world leaked on cycle " + cycle, 0, harness.worldCount());
        }
    }
}
