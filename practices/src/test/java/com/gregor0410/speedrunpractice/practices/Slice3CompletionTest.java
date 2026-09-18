package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.checkpoint.CheckpointManager;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutManager;
import com.gregor0410.speedrunpractice.common.scenario.ScenarioDefinition;
import com.gregor0410.speedrunpractice.common.scenario.ScenarioLoader;
import com.gregor0410.speedrunpractice.common.stats.InMemoryPracticeStatistics;
import com.gregor0410.speedrunpractice.common.stats.PracticeStatistics;
import com.gregor0410.speedrunpractice.common.timer.MonotonicPracticeTimer;
import com.gregor0410.speedrunpractice.common.timer.PracticeTimer;
import com.gregor0410.speedrunpractice.testsupport.ScenarioTestHarness;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Slice 3 completion coverage: every scenario's finish conditions plus the
 * negative cases (wrong dimension, missing target, unknown goal, manual).
 */
public class Slice3CompletionTest {
    private static ScenarioEngine engine(ScenarioTestHarness harness) {
        PracticeTimer timer = new MonotonicPracticeTimer();
        PracticeStatistics stats = new InMemoryPracticeStatistics();
        CheckpointManager checkpoints = new CheckpointManager.InMemoryCheckpointManager(harness, timer);
        LoadoutManager loadouts = new LoadoutManager.InMemoryLoadoutManager();
        loadouts.save(new Loadout("overworld_default", Collections.<Loadout.Item>emptyList()));
        return new ScenarioEngine(harness, checkpoints, timer, stats, loadouts);
    }

    private static void give(ScenarioTestHarness harness, String itemId, int count) throws Exception {
        harness.players().applyLoadout(ScenarioTestHarness.player("p1"), new Loadout("hand",
                Collections.singletonList(new Loadout.Item(itemId, 0, count))));
    }

    // Overworld --------------------------------------------------------------------

    @Test
    public void overworldFinishesOnEnteringNether() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new OverworldScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 1L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.setPlayerDimension("p1", PracticeDimension.NETHER);
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void overworldDimensionEventFinishes() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new OverworldScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 1L);
        assertTrue(engine.fireEvent(new PracticeEvent.DimensionChangedEvent(
                PracticeDimension.OVERWORLD, PracticeDimension.NETHER)).isFinished());
        engine.stopCurrent();
    }

    @Test
    public void overworldReachStructure() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("village", new PracticePosition(300.0, 64.0, 300.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("spawn.structure", "village");
        settings.set("overworld.goal", "reach_structure");
        settings.set("overworld.completeRadius", "8");
        engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(0.0, 64.0, 0.0));
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(300.0, 64.0, 300.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void overworldObtainItem() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("overworld.goal", "obtain_item");
        settings.set("overworld.item", "minecraft:ender_pearl");
        settings.set("overworld.count", "4");
        engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        assertFalse(engine.tickCurrent().isFinished());
        give(harness, "minecraft:ender_pearl", 4);
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void overworldManualAndUnknownGoalsNeverFinish() throws Exception {
        for (String goal : Arrays.asList("manual", "bogus")) {
            ScenarioTestHarness harness = new ScenarioTestHarness();
            ScenarioEngine engine = engine(harness);
            PracticeSettings settings = new PracticeSettings();
            settings.set("overworld.goal", goal);
            engine.startScenario(new OverworldScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
            harness.setPlayerDimension("p1", PracticeDimension.NETHER);
            assertFalse("goal " + goal, engine.tickCurrent().isFinished());
            engine.stopCurrent();
        }
    }

    // Nether -------------------------------------------------------------------------

    @Test
    public void netherExitsByDefault() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new NetherScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 1L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.setPlayerDimension("p1", PracticeDimension.OVERWORLD);
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void netherReachAndObtainGoals() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("bastion_remnant", new PracticePosition(400.0, 70.0, -100.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("nether.goal", "reach_bastion");
        engine.startScenario(new NetherScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(400.0, 70.0, -100.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();

        ScenarioTestHarness pearls = new ScenarioTestHarness();
        ScenarioEngine pearlEngine = engine(pearls);
        PracticeSettings pearlSettings = new PracticeSettings();
        pearlSettings.set("nether.goal", "obtain_pearls");
        pearlEngine.startScenario(new NetherScenario(), pearlSettings,
                ScenarioTestHarness.player("p1"), 1L);
        give(pearls, "minecraft:ender_pearl", 15);
        assertFalse(pearlEngine.tickCurrent().isFinished());
        give(pearls, "minecraft:ender_pearl", 16);
        assertTrue(pearlEngine.tickCurrent().isFinished());
        pearlEngine.stopCurrent();
    }

    @Test
    public void netherManualNeverFinishes() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("nether.goal", "manual");
        engine.startScenario(new NetherScenario(), settings, ScenarioTestHarness.player("p1"), 1L);
        harness.setPlayerDimension("p1", PracticeDimension.OVERWORLD);
        give(harness, "minecraft:ender_pearl", 64);
        assertFalse(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    // Buried treasure -------------------------------------------------------------------

    @Test
    public void buriedTreasureSpawnsNearAndFinishesAtChest() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("buried_treasure", new PracticePosition(300.0, 63.0, 300.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new BuriedTreasureScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 4L);
        assertEquals(312.0, harness.positionOf("p1").x(), 0.0);
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(300.0, 63.0, 300.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void buriedTreasureChunkArrivalDoesNotFinish() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("buried_treasure", new PracticePosition(300.0, 63.0, 300.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new BuriedTreasureScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 4L);
        // Same chunk (16x16) as the chest but outside the 4-block radius.
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(303.0, 63.0, 303.0));
        assertFalse(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void buriedTreasureWithoutChestNeverFinishes() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new BuriedTreasureScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 4L);
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(0.0, 64.0, 0.0));
        assertFalse(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    // Bastion ----------------------------------------------------------------------------

    @Test
    public void bastionPearlGoal() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("bastion_remnant", new PracticePosition(100.0, 70.0, 200.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("bastion.targetPearls", "4");
        engine.startScenario(new BastionScenario(), settings, ScenarioTestHarness.player("p1"), 7L);
        give(harness, "minecraft:ender_pearl", 3);
        assertFalse(engine.tickCurrent().isFinished());
        give(harness, "minecraft:ender_pearl", 4);
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void bastionLeaveAfterEntering() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("bastion_remnant", new PracticePosition(100.0, 70.0, 200.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("bastion.goal", "leave_bastion");
        settings.set("spawn.mode", "outside");
        settings.set("spawn.distance", "40");
        ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("p1");
        engine.startScenario(new BastionScenario(), settings, player, 7L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(player, new PracticePosition(100.0, 70.0, 200.0));
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(player, new PracticePosition(0.0, 64.0, 0.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void bastionManualNeverFinishes() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("bastion_remnant", new PracticePosition(100.0, 70.0, 200.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("bastion.goal", "manual");
        engine.startScenario(new BastionScenario(), settings, ScenarioTestHarness.player("p1"), 7L);
        give(harness, "minecraft:ender_pearl", 64);
        assertFalse(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    // Fortress ------------------------------------------------------------------------------

    @Test
    public void fortressFindAndBlaze() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("fortress", new PracticePosition(500.0, 60.0, -300.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("fortress.mode", "find");
        settings.set("spawn.distance", "64");
        engine.startScenario(new FortressScenario(), settings, ScenarioTestHarness.player("p1"), 3L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(500.0, 60.0, -300.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();

        ScenarioTestHarness blaze = new ScenarioTestHarness();
        blaze.scriptStructure("fortress", new PracticePosition(500.0, 60.0, -300.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine blazeEngine = engine(blaze);
        PracticeSettings blazeSettings = new PracticeSettings();
        blazeSettings.set("fortress.mode", "blaze");
        blazeEngine.startScenario(new FortressScenario(), blazeSettings,
                ScenarioTestHarness.player("p1"), 3L);
        give(blaze, "minecraft:blaze_rod", 7);
        assertFalse(blazeEngine.tickCurrent().isFinished());
        give(blaze, "minecraft:blaze_rod", 8);
        assertTrue(blazeEngine.tickCurrent().isFinished());
        blazeEngine.stopCurrent();
    }

    @Test
    public void fortressExitNeedsEnterThenLeave() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("fortress", new PracticePosition(500.0, 60.0, -300.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("fortress.mode", "exit");
        ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("p1");
        engine.startScenario(new FortressScenario(), settings, player, 3L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(player, new PracticePosition(0.0, 64.0, 0.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void fortressUnknownModeNeverFinishes() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("fortress", new PracticePosition(500.0, 60.0, -300.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("fortress.mode", "bogus");
        engine.startScenario(new FortressScenario(), settings, ScenarioTestHarness.player("p1"), 3L);
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(500.0, 60.0, -300.0));
        assertFalse(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    // Blind travel ---------------------------------------------------------------------------------

    @Test
    public void blindTravelFinishesOnOverworldExitWithMeasurement() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new BlindTravelScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 21L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(50.0, 70.0, 60.0));
        harness.setPlayerDimension("p1", PracticeDimension.OVERWORLD);
        assertTrue(engine.tickCurrent().isFinished());
        assertEquals("50,70,60", engine.currentContext().getAttribute(BlindTravelScenario.BLIND_EXIT_ATTRIBUTE));
        assertEquals("1000,64,1000",
                engine.currentContext().getAttribute(BlindTravelScenario.BLIND_STRONGHOLD_ATTRIBUTE));
        double distance = Double.parseDouble(
                (String) engine.currentContext().getAttribute(BlindTravelScenario.BLIND_DISTANCE_ATTRIBUTE));
        assertEquals(Math.hypot(950.0, 940.0), distance, 1e-6);
        engine.stopCurrent();
    }

    @Test
    public void blindTravelFinishesWithoutStronghold() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new BlindTravelScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 21L);
        harness.setPlayerDimension("p1", PracticeDimension.OVERWORLD);
        assertTrue(engine.tickCurrent().isFinished());
        assertEquals("-1", engine.currentContext().getAttribute(BlindTravelScenario.BLIND_DISTANCE_ATTRIBUTE));
        engine.stopCurrent();
    }

    // Post blind --------------------------------------------------------------------------------------

    @Test
    public void postblindReachStronghold() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("postblind.minDist", "100");
        settings.set("postblind.maxDist", "100");
        engine.startScenario(new PostBlindScenario(), settings, ScenarioTestHarness.player("p1"), 5L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(1000.0, 64.0, 1000.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void postblindPortalRoomUsesMetadata() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        java.util.Map<String, String> metadata = new java.util.HashMap<String, String>();
        metadata.put("portal_room", "1010,20,1030");
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0), metadata);
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("postblind.goal", "reach_portal_room");
        settings.set("postblind.minDist", "100");
        settings.set("postblind.maxDist", "100");
        engine.startScenario(new PostBlindScenario(), settings, ScenarioTestHarness.player("p1"), 5L);
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(1000.0, 64.0, 1000.0));
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(1010.0, 20.0, 1030.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void postblindManualNeverFinishes() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("postblind.goal", "manual");
        engine.startScenario(new PostBlindScenario(), settings, ScenarioTestHarness.player("p1"), 5L);
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(1000.0, 64.0, 1000.0));
        assertFalse(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    // Stronghold ----------------------------------------------------------------------------------------

    @Test
    public void strongholdNavigationReachesPortalRoom() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        java.util.Map<String, String> metadata = new java.util.HashMap<String, String>();
        metadata.put("portal_room", "1010,20,1030");
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0), metadata);
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("stronghold.mode", "navigation");
        engine.startScenario(new StrongholdScenario(), settings, ScenarioTestHarness.player("p1"), 9L);
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(0.0, 64.0, 0.0));
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(1010.0, 20.0, 1030.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void strongholdPortalRoomModeEntersEnd() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        PracticeSettings settings = new PracticeSettings();
        settings.set("stronghold.mode", "portal_room");
        engine.startScenario(new StrongholdScenario(), settings, ScenarioTestHarness.player("p1"), 9L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.setPlayerDimension("p1", PracticeDimension.END);
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void strongholdEntryDefaultsToManual() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new StrongholdScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 9L);
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(1000.0, 64.0, 1000.0));
        assertFalse(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    // End + one cycle --------------------------------------------------------------------------------------

    @Test
    public void endIgnoresForeignDragonEvents() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new EndScenario(), new PracticeSettings(), ScenarioTestHarness.player("p1"), 3L);
        assertFalse(engine.fireEvent(new PracticeEvent.DragonKilledEvent("another-world")).isFinished());
        assertTrue(engine.fireEvent(new PracticeEvent.DragonKilledEvent(
                engine.currentContext().world().handleId())).isFinished());
        engine.stopCurrent();
    }

    @Test
    public void oneCycleDragonEventFinishes() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new OneCycleScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 6L);
        assertTrue(engine.fireEvent(new PracticeEvent.DragonKilledEvent(
                engine.currentContext().world().handleId())).isFinished());
        engine.stopCurrent();
    }

    // Custom --------------------------------------------------------------------------------------------------

    private static ScenarioDefinition custom(String completionJson) throws Exception {
        return ScenarioLoader.loadFromJson("{\"id\": \"hunt\", \"type\": \"custom\","
                + "\"world\": {\"dimension\": \"overworld\"}, \"seed\": {\"source\": \"random\"},"
                + "\"spawn\": {\"type\": \"world_spawn\"},"
                + "\"timer\": {\"start\": \"scenario_load\", \"stop\": \"scenario_complete\"},"
                + "\"completion\": " + completionJson + "}", "test");
    }

    @Test
    public void customDimensionEntry() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new CustomScenario(custom("{\"type\": \"dimension_entry\","
                + " \"dimension\": \"nether\"}")), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 8L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.setPlayerDimension("p1", PracticeDimension.NETHER);
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void customStructureReached() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new CustomScenario(custom("{\"type\": \"structure_reached\","
                + " \"structure\": \"stronghold\", \"radius\": 8}")), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 8L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(1000.0, 64.0, 1000.0));
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void customDragonDeath() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new CustomScenario(custom("{\"type\": \"dragon_death\"}")),
                new PracticeSettings(), ScenarioTestHarness.player("p1"), 8L);
        assertFalse(engine.tickCurrent().isFinished());
        harness.setLivingDragon(engine.currentContext().world().handleId(), false);
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void customManualNeverFinishes() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new CustomScenario(custom("{\"type\": \"manual\"}")),
                new PracticeSettings(), ScenarioTestHarness.player("p1"), 8L);
        harness.setPlayerDimension("p1", PracticeDimension.NETHER);
        harness.setLivingDragon(engine.currentContext().world().handleId(), false);
        harness.players().teleport(ScenarioTestHarness.player("p1"), new PracticePosition(1000.0, 64.0, 1000.0));
        assertFalse(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void customRequiresSupportedCapability() throws Exception {
        ScenarioDefinition definition = ScenarioLoader.loadFromJson(
                "{\"id\": \"needs\", \"type\": \"custom\", \"requires\": \"dragon_force_perch\"}", "test");
        assertNotNull(definition);
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness);
        engine.startScenario(new CustomScenario(definition), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 8L);
        engine.stopCurrent();
    }
}
