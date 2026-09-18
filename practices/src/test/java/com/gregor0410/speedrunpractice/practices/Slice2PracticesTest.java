package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.checkpoint.CheckpointManager;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
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

import java.io.File;
import java.io.FilenameFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Slice 2 coverage: the newer practices (bastion, fortress, blind travel,
 * one cycle), legacy-parity paths of the migrated practices, custom
 * definitions, and validation of every shipped {@code definitions/} file.
 */
public class Slice2PracticesTest {
    private static ScenarioEngine engine(ScenarioTestHarness harness, LoadoutManager loadouts) {
        PracticeTimer timer = new MonotonicPracticeTimer();
        PracticeStatistics stats = new InMemoryPracticeStatistics();
        CheckpointManager checkpoints = new CheckpointManager.InMemoryCheckpointManager(harness, timer);
        return new ScenarioEngine(harness, checkpoints, timer, stats, loadouts);
    }

    private static LoadoutManager loadouts() {
        LoadoutManager manager = new LoadoutManager.InMemoryLoadoutManager();
        manager.save(new Loadout("overworld_default", Collections.<Loadout.Item>emptyList()));
        return manager;
    }

    private static Map<String, String> metadata(String key, String value) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(key, value);
        return map;
    }

    private static double horizontalDistance(PracticePosition a, PracticePosition b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int countItem(Loadout loadout, String itemId) {
        int total = 0;
        for (Loadout.Item item : loadout.items()) {
            if (itemId.equals(item.itemId())) {
                total += item.count();
            }
        }
        return total;
    }

    private static Set<Integer> slotsOf(Loadout loadout, String itemId) {
        Set<Integer> slots = new HashSet<Integer>();
        for (Loadout.Item item : loadout.items()) {
            if (itemId.equals(item.itemId())) {
                slots.add(item.slot());
            }
        }
        return slots;
    }

    @Test
    public void bastionUnknownTypeFallsBackToRandom() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("bastion_remnant", new PracticePosition(100.0, 70.0, 200.0),
                metadata(StructureAdapter.StructureLocation.BASTION_TYPE_KEY, "housing"));
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("bastion.type", "castle");
        settings.set("spawn.mode", "outside");
        settings.set("spawn.distance", "40");
        engine.startScenario(new BastionScenario(), settings, ScenarioTestHarness.player("p1"), 7L);
        assertEquals(140.0, harness.positionOf("p1").x(), 0.0);
        assertEquals(200.0, harness.positionOf("p1").z(), 0.0);
        engine.stopCurrent();
    }

    @Test
    public void bastionFoodAndHealthApplied() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("bastion_remnant", new PracticePosition(100.0, 70.0, 200.0),
                metadata(StructureAdapter.StructureLocation.BASTION_TYPE_KEY, "housing"));
        ScenarioEngine engine = engine(harness, loadouts());
        ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("p1");
        PracticeSettings settings = new PracticeSettings();
        settings.set("bastion.health", "10");
        settings.set("bastion.food", "7");
        engine.startScenario(new BastionScenario(), settings, player, 7L);
        assertEquals(10.0, harness.players().getHealth(player), 0.0);
        assertEquals(7, harness.players().getFood(player));
        engine.stopCurrent();
    }

    @Test
    public void bastionBadFoodIgnored() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("bastion_remnant", new PracticePosition(100.0, 70.0, 200.0),
                metadata(StructureAdapter.StructureLocation.BASTION_TYPE_KEY, "housing"));
        ScenarioEngine engine = engine(harness, loadouts());
        ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("p1");
        PracticeSettings settings = new PracticeSettings();
        settings.set("bastion.food", "a lot");
        engine.startScenario(new BastionScenario(), settings, player, 7L);
        assertEquals(20, harness.players().getFood(player));
        engine.stopCurrent();
    }

    @Test
    public void bastionRandomExteriorIsSeededAtDistance() throws Exception {
        PracticePosition structure = new PracticePosition(100.0, 70.0, 200.0);
        List<PracticePosition> starts = new ArrayList<PracticePosition>();
        for (int i = 0; i < 2; i++) {
            ScenarioTestHarness harness = new ScenarioTestHarness();
            harness.scriptStructure("bastion_remnant", structure, metadata(StructureAdapter.StructureLocation.BASTION_TYPE_KEY, "housing"));
            ScenarioEngine engine = engine(harness, loadouts());
            PracticeSettings settings = new PracticeSettings();
            settings.set("spawn.mode", "random_exterior");
            settings.set("spawn.distance", "40");
            engine.startScenario(new BastionScenario(), settings, ScenarioTestHarness.player("p1"), 99L);
            starts.add(harness.positionOf("p1"));
            engine.stopCurrent();
        }
        assertEquals(starts.get(0).x(), starts.get(1).x(), 0.0);
        assertEquals(starts.get(0).z(), starts.get(1).z(), 0.0);
        assertEquals(40.0, horizontalDistance(starts.get(0), structure), 1e-6);
    }

    @Test
    public void bastionEntranceStartsAtStructure() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("bastion_remnant", new PracticePosition(100.0, 70.0, 200.0),
                metadata(StructureAdapter.StructureLocation.BASTION_TYPE_KEY, "housing"));
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("spawn.mode", "entrance");
        engine.startScenario(new BastionScenario(), settings, ScenarioTestHarness.player("p1"), 7L);
        assertEquals(100.0, harness.positionOf("p1").x(), 0.0);
        assertEquals(200.0, harness.positionOf("p1").z(), 0.0);
        engine.stopCurrent();
    }

    @Test
    public void bastionMissingThrowsReadably() {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness, loadouts());
        try {
            engine.startScenario(new BastionScenario(), new PracticeSettings(),
                    ScenarioTestHarness.player("p1"), 7L);
            fail("expected PracticeException when no bastion is in range");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("Bastion"));
        }
    }

    @Test
    public void fortressModes() throws Exception {
        PracticePosition fortress = new PracticePosition(500.0, 60.0, -300.0);
        String[] insideModes = {"enter", "blaze", "navigation", "exit", "bogus"};
        for (String mode : insideModes) {
            ScenarioTestHarness harness = new ScenarioTestHarness();
            harness.scriptStructure("fortress", fortress, Collections.<String, String>emptyMap());
            ScenarioEngine engine = engine(harness, loadouts());
            PracticeSettings settings = new PracticeSettings();
            settings.set("fortress.mode", mode);
            engine.startScenario(new FortressScenario(), settings, ScenarioTestHarness.player("p1"), 3L);
            assertEquals("mode " + mode, 500.0, harness.positionOf("p1").x(), 0.0);
            assertEquals("mode " + mode, -300.0, harness.positionOf("p1").z(), 0.0);
            engine.stopCurrent();
        }

        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("fortress", fortress, Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("fortress.mode", "find");
        settings.set("spawn.distance", "64");
        engine.startScenario(new FortressScenario(), settings, ScenarioTestHarness.player("p1"), 3L);
        assertEquals(564.0, harness.positionOf("p1").x(), 0.0);
        engine.stopCurrent();
    }

    @Test
    public void fortressMissingThrowsReadably() {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness, loadouts());
        try {
            engine.startScenario(new FortressScenario(), new PracticeSettings(),
                    ScenarioTestHarness.player("p1"), 3L);
            fail("expected PracticeException when no fortress is in range");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("Fortress"));
        }
    }

    @Test
    public void blindTravelTargetsDistanceWithMats() throws Exception {
        PracticePosition first = null;
        for (int i = 0; i < 2; i++) {
            ScenarioTestHarness harness = new ScenarioTestHarness();
            ScenarioEngine engine = engine(harness, loadouts());
            PracticeSettings settings = new PracticeSettings();
            settings.set("blind.targetDistance", "1000");
            engine.startScenario(new BlindTravelScenario(), settings, ScenarioTestHarness.player("p1"), 21L);
            PracticePosition position = harness.positionOf("p1");
            assertEquals(1000.0, horizontalDistance(position, new PracticePosition(0.0, 64.0, 0.0)), 1e-6);
            if (first == null) {
                first = position;
            } else {
                assertEquals(first.x(), position.x(), 0.0);
                assertEquals(first.z(), position.z(), 0.0);
            }
            Loadout applied = harness.appliedLoadout("p1");
            assertNotNull(applied);
            assertEquals("blind_travel_mats", applied.id());
            assertEquals(10, countItem(applied, "minecraft:obsidian"));
            engine.stopCurrent();
        }
    }

    @Test
    public void blindTravelHonorsNoMats() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("blind.givePortalMats", "false");
        engine.startScenario(new BlindTravelScenario(), settings, ScenarioTestHarness.player("p1"), 21L);
        assertNull(harness.appliedLoadout("p1"));
        engine.stopCurrent();
    }

    @Test
    public void blindTravelLoadoutWinsOverMats() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("loadout", "overworld_default");
        engine.startScenario(new BlindTravelScenario(), settings, ScenarioTestHarness.player("p1"), 21L);
        assertEquals("overworld_default", harness.appliedLoadout("p1").id());
        engine.stopCurrent();
    }

    @Test
    public void postblindEyesLoadoutAtFixedDistance() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticePosition stronghold = new PracticePosition(1000.0, 64.0, 1000.0);
        harness.scriptStructure("stronghold", stronghold, Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("postblind.minDist", "100");
        settings.set("postblind.maxDist", "100");
        settings.set("postblind.eyes", "12");
        engine.startScenario(new PostBlindScenario(), settings, ScenarioTestHarness.player("p1"), 5L);
        assertEquals(100.0, horizontalDistance(harness.positionOf("p1"), stronghold), 1e-6);
        Loadout applied = harness.appliedLoadout("p1");
        assertNotNull(applied);
        assertEquals(12, countItem(applied, "minecraft:ender_eye"));
        assertEquals(16, countItem(applied, "minecraft:ender_pearl"));
        engine.stopCurrent();
    }

    @Test
    public void postblindSwapsMinMax() throws Exception {
        List<PracticePosition> starts = new ArrayList<PracticePosition>();
        String[][] ranges = {{"200", "100"}, {"100", "200"}};
        for (String[] range : ranges) {
            ScenarioTestHarness harness = new ScenarioTestHarness();
            harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                    Collections.<String, String>emptyMap());
            ScenarioEngine engine = engine(harness, loadouts());
            PracticeSettings settings = new PracticeSettings();
            settings.set("postblind.minDist", range[0]);
            settings.set("postblind.maxDist", range[1]);
            engine.startScenario(new PostBlindScenario(), settings, ScenarioTestHarness.player("p1"), 5L);
            starts.add(harness.positionOf("p1"));
            engine.stopCurrent();
        }
        assertEquals(starts.get(0).x(), starts.get(1).x(), 0.0);
        assertEquals(starts.get(0).z(), starts.get(1).z(), 0.0);
    }

    @Test
    public void postblindLoadoutWinsOverEyes() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("loadout", "overworld_default");
        settings.set("postblind.eyes", "12");
        engine.startScenario(new PostBlindScenario(), settings, ScenarioTestHarness.player("p1"), 5L);
        assertEquals("overworld_default", harness.appliedLoadout("p1").id());
        engine.stopCurrent();
    }

    @Test
    public void strongholdPortalRoomUsesMetadata() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                metadata("portal_room", "1010,20,1030"));
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("stronghold.mode", "portal_room");
        engine.startScenario(new StrongholdScenario(), settings, ScenarioTestHarness.player("p1"), 9L);
        assertEquals(1010.0, harness.positionOf("p1").x(), 0.0);
        assertEquals(20.0, harness.positionOf("p1").y(), 0.0);
        assertEquals(1030.0, harness.positionOf("p1").z(), 0.0);
        engine.stopCurrent();
    }

    @Test
    public void strongholdPortalRoomFallsBackToStairs() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("stronghold", new PracticePosition(1000.0, 64.0, 1000.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("stronghold.mode", "portal_room");
        settings.set("stronghold.eyes", "8");
        engine.startScenario(new StrongholdScenario(), settings, ScenarioTestHarness.player("p1"), 9L);
        assertEquals(1000.0, harness.positionOf("p1").x(), 0.0);
        assertEquals(8, countItem(harness.appliedLoadout("p1"), "minecraft:ender_eye"));
        engine.stopCurrent();
    }

    @Test
    public void strongholdMissingThrowsReadably() {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness, loadouts());
        try {
            engine.startScenario(new StrongholdScenario(), new PracticeSettings(),
                    ScenarioTestHarness.player("p1"), 9L);
            fail("expected PracticeException when no stronghold is in range");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("Stronghold"));
        }
    }

    @Test
    public void netherStructureStartAndFallback() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("fortress", new PracticePosition(400.0, 70.0, -100.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness, loadouts());
        PracticeSettings settings = new PracticeSettings();
        settings.set("spawn.structure", "fortress");
        engine.startScenario(new NetherScenario(), settings, ScenarioTestHarness.player("p1"), 2L);
        assertEquals(400.0, harness.positionOf("p1").x(), 0.0);
        engine.stopCurrent();

        ScenarioTestHarness fallback = new ScenarioTestHarness();
        ScenarioEngine fallbackEngine = engine(fallback, loadouts());
        PracticeSettings missing = new PracticeSettings();
        missing.set("spawn.structure", "fortress");
        fallbackEngine.startScenario(new NetherScenario(), missing, ScenarioTestHarness.player("p1"), 2L);
        assertEquals(0.0, fallback.positionOf("p1").x(), 0.0);
        fallbackEngine.stopCurrent();
    }

    @Test
    public void buriedTreasureStartAndFallback() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.scriptStructure("buried_treasure", new PracticePosition(300.0, 63.0, 300.0),
                Collections.<String, String>emptyMap());
        ScenarioEngine engine = engine(harness, loadouts());
        engine.startScenario(new BuriedTreasureScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 4L);
        // Near-spawn: chest x + the default 12-block offset, so reaching the
        // chest stays a meaningful completion (plan section 24).
        assertEquals(312.0, harness.positionOf("p1").x(), 0.0);
        engine.stopCurrent();

        ScenarioTestHarness fallback = new ScenarioTestHarness();
        ScenarioEngine fallbackEngine = engine(fallback, loadouts());
        fallbackEngine.startScenario(new BuriedTreasureScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 4L);
        assertEquals(0.0, fallback.positionOf("p1").x(), 0.0);
        fallbackEngine.stopCurrent();
    }

    @Test
    public void onecycleDefaultLoadoutMatchesEndKits() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness, loadouts());
        engine.startScenario(new OneCycleScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 6L);
        Loadout applied = harness.appliedLoadout("p1");
        assertNotNull(applied);
        assertEquals(10, countItem(applied, "minecraft:white_bed"));
        Set<Integer> beds = slotsOf(applied, "minecraft:white_bed");
        assertTrue(beds.contains(1));
        for (int slot = 9; slot <= 17; slot++) {
            assertTrue("missing bed in slot " + slot, beds.contains(slot));
        }
        assertEquals(100.0, harness.positionOf("p1").x(), 0.0);
        String worldHandle = engine.currentContext().world().handleId();
        assertTrue(harness.guiCalls().contains("forcePerch:" + worldHandle));
        engine.stopCurrent();
    }

    @Test
    public void onecycleCompletesWhenDragonDies() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness, loadouts());
        engine.startScenario(new OneCycleScenario(), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 6L);
        assertTrue(!engine.tickCurrent().isFinished());
        harness.setLivingDragon(engine.currentContext().world().handleId(), false);
        assertTrue(engine.tickCurrent().isFinished());
        engine.stopCurrent();
    }

    @Test
    public void customSpawnCoordinates() throws Exception {
        String json = "{\"id\":\"test_custom\",\"type\":\"custom\",\"displayName\":\"T\","
                + "\"world\":{\"dimension\":\"overworld\"},\"seed\":{\"source\":\"random\"},"
                + "\"spawn\":{\"type\":\"custom\",\"x\":11,\"y\":65,\"z\":12},"
                + "\"timer\":{\"start\":\"player_move\",\"stop\":\"manual\"}}";
        ScenarioDefinition definition = ScenarioLoader.loadFromJson(json, "test");
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness, loadouts());
        engine.startScenario(new CustomScenario(definition), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 8L);
        assertEquals(11.0, harness.positionOf("p1").x(), 0.0);
        assertEquals(65.0, harness.positionOf("p1").y(), 0.0);
        assertEquals(12.0, harness.positionOf("p1").z(), 0.0);
        engine.stopCurrent();
    }

    @Test
    public void customRandomNetherHonorsDistance() throws Exception {
        String json = "{\"id\":\"test_rn\",\"type\":\"custom\","
                + "\"world\":{\"dimension\":\"nether\"},\"seed\":{\"source\":\"random\"},"
                + "\"spawn\":{\"type\":\"random_nether\",\"distance\":500},"
                + "\"timer\":{\"start\":\"player_move\",\"stop\":\"manual\"}}";
        ScenarioDefinition definition = ScenarioLoader.loadFromJson(json, "test");
        ScenarioTestHarness harness = new ScenarioTestHarness();
        ScenarioEngine engine = engine(harness, loadouts());
        engine.startScenario(new CustomScenario(definition), new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 8L);
        PracticePosition spawn = new PracticePosition(0.0, 64.0, 0.0);
        assertEquals(500.0, horizontalDistance(harness.positionOf("p1"), spawn), 1e-6);
        engine.stopCurrent();
    }

    @Test
    public void shippedDefinitionsValidate() throws Exception {
        File practicesDir = new File("../definitions/practices");
        File loadoutsDir = new File("../definitions/loadouts");
        assertTrue("missing definitions dir (run from the practices module): "
                + practicesDir.getAbsolutePath(), practicesDir.isDirectory());
        assertTrue("missing loadout dir: " + loadoutsDir.getAbsolutePath(), loadoutsDir.isDirectory());

        Set<String> loadoutIds = new HashSet<String>();
        int loadoutFiles = 0;
        for (File file : listJson(loadoutsDir)) {
            loadoutFiles++;
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Loadout loadout;
            try {
                loadout = Loadout.fromJson(json);
            } catch (PracticeException bad) {
                fail(file.getName() + ": " + bad.getMessage());
                return;
            }
            assertEquals("id must match " + file.getName(),
                    file.getName().replace(".json", ""), loadout.id());
            loadoutIds.add(loadout.id());
        }
        assertTrue("expected the shipped loadouts, found " + loadoutFiles, loadoutFiles >= 8);

        int practiceFiles = 0;
        for (File file : listJson(practicesDir)) {
            practiceFiles++;
            ScenarioDefinition definition;
            try {
                definition = ScenarioLoader.loadFromFile(file.toPath());
            } catch (PracticeException bad) {
                fail(file.getName() + ": " + bad.getMessage());
                return;
            }
            assertEquals("id must match " + file.getName(),
                    file.getName().replace(".json", ""), definition.id().value());
            assertNotNull(definition.type());
            assertNotNull(definition.dimension());
            if (definition.loadout() != null) {
                assertTrue("unknown loadout \"" + definition.loadout() + "\" in " + file.getName(),
                        loadoutIds.contains(definition.loadout()));
            }
        }
        assertTrue("expected the shipped practices, found " + practiceFiles, practiceFiles >= 13);
    }

    private static File[] listJson(File dir) {
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                return name.endsWith(".json");
            }
        });
        return files == null ? new File[0] : files;
    }
}
