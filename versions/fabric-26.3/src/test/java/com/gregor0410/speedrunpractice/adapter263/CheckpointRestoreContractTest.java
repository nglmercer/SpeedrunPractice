package com.gregor0410.speedrunpractice.adapter263;

import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.checkpoint.CheckpointManager;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.timer.MonotonicPracticeTimer;
import com.gregor0410.speedrunpractice.common.timer.PracticeTimer;
import com.gregor0410.speedrunpractice.testsupport.ScenarioTestHarness;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Full checkpoint save/mutate/restore contract for 26.3 (no Minecraft
 * needed): every checkpointed field (seed, dimension, position + rotation,
 * health, food, saturation, XP, inventory incl. armor and offhand, selected
 * slot, effects, scenario state, timer) is seeded, snapshotted, mutated, and
 * asserted back to the snapshotted values after restore.
 */
public class CheckpointRestoreContractTest {
    private static final long SEED = 424242L;
    private static final long OTHER_SEED = 777001L;

    private static final PracticePosition SPAWN =
            new PracticePosition(100.5, 65.0, -200.25, 135.0f, -30.0f);
    private static final double HEALTH = 13.5;
    private static final int FOOD = 14;
    private static final float SATURATION = 7.5f;
    private static final int XP_LEVEL = 17;
    private static final int XP_POINTS = 42;
    private static final int SLOT = 5;
    private static final List<String> EFFECTS =
            Arrays.asList("minecraft:speed", "minecraft:jump_boost");
    private static final long TIMER_MS = 123456L;

    private static final class RecordingScenario implements PracticeScenario {
        private final Map<String, String> state = new LinkedHashMap<String, String>();

        @Override
        public PracticeId id() {
            return PracticeId.of("overworld");
        }

        @Override
        public PracticeType type() {
            return PracticeType.OVERWORLD;
        }

        @Override
        public void prepare(PracticeContext context) {
        }

        @Override
        public void start(PracticeContext context) {
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

        @Override
        public PracticeCheckpoint.ScenarioSnapshot captureState(PracticeContext context) {
            return new PracticeCheckpoint.MapScenarioSnapshot("recording",
                    new LinkedHashMap<String, String>(state));
        }

        @Override
        public void restoreState(PracticeContext context, PracticeCheckpoint.ScenarioSnapshot snapshot) {
            state.clear();
            if (snapshot != null) {
                state.putAll(snapshot.data());
            }
        }
    }

    private static final class Fixture {
        final ScenarioTestHarness harness = new ScenarioTestHarness(GameVersion.V_26_3);
        final PracticeTimer timer = new MonotonicPracticeTimer();
        final CheckpointManager manager =
                new CheckpointManager.InMemoryCheckpointManager(harness, timer);
        final RecordingScenario scenario = new RecordingScenario();
        final PracticeWorld world;
        final ScenarioTestHarness.FakePlayer player = ScenarioTestHarness.player("p1");
        final PracticeContext context;

        Fixture() throws PracticeException {
            world = harness.worlds().createPracticeWorld(SEED,
                    WorldAdapter.PracticeWorldOptions.builder(PracticeDimension.OVERWORLD).build());
            PracticeSession session = new PracticeSession(PracticeId.of("overworld"), SEED);
            context = new PracticeContext(session, harness, world, player,
                    new PracticeSettings(), SEED);
        }
    }

    @Test
    public void fullCheckpointRestoresEveryFieldAfterMutation() throws Exception {
        Fixture fixture = new Fixture();
        applyOriginalPlayer(fixture);
        Map<String, String> scenarioState = originalScenarioState();
        fixture.scenario.state.putAll(scenarioState);
        fixture.timer.setElapsedMs(TIMER_MS);

        fixture.manager.save(fixture.context, fixture.scenario);

        // Mutate everything: seed + dimension drift recreates the world first.
        fixture.harness.worlds().resetPracticeWorld(fixture.world, OTHER_SEED,
                WorldAdapter.PracticeWorldOptions.builder(PracticeDimension.NETHER).build());
        fixture.context.setSeed(OTHER_SEED);
        applyMutatedPlayer(fixture);
        fixture.scenario.state.clear();
        fixture.scenario.state.put("target", "minecraft:desert_pyramid");
        fixture.scenario.state.put("entered", "false");
        fixture.timer.setElapsedMs(999L);
        fixture.timer.start();

        fixture.manager.restore(fixture.context, fixture.scenario);

        // Seed + dimension + context seed come back with the world.
        assertEquals(SEED, fixture.context.seed());
        assertEquals(SEED, fixture.context.session().seed());
        assertEquals(SEED, fixture.context.world().seed());
        assertEquals(PracticeDimension.OVERWORLD, fixture.context.world().dimension());
        assertPlayerMatchesOriginal(fixture);
        assertEquals(scenarioState, fixture.scenario.state);
        assertEquals("recording", captureScenarioType(fixture));
        // Stopped timer restores exactly.
        assertFalse(fixture.timer.isRunning());
        assertEquals(TIMER_MS, fixture.timer.elapsedMs());
    }

    @Test
    public void checkpointRestoresRunningTimer() throws Exception {
        Fixture fixture = new Fixture();
        applyOriginalPlayer(fixture);
        fixture.scenario.state.putAll(originalScenarioState());
        fixture.timer.setElapsedMs(5000L);
        fixture.timer.start();

        fixture.manager.save(fixture.context, fixture.scenario);

        fixture.timer.stop();
        fixture.timer.setElapsedMs(60000L);
        fixture.harness.players().teleport(fixture.player,
                new PracticePosition(-300.0, 80.0, 44.0, -90.0f, 10.0f));

        fixture.manager.restore(fixture.context, fixture.scenario);

        assertTrue(fixture.timer.isRunning());
        assertTrue("running timer should resume near 5000ms, was " + fixture.timer.elapsedMs(),
                fixture.timer.elapsedMs() >= 5000L && fixture.timer.elapsedMs() < 10000L);
        assertEquals(SPAWN.x(), fixture.harness.positionOf("p1").x(), 0.0);
    }

    @Test
    public void dimensionDriftAloneRecreatesWorld() throws Exception {
        Fixture fixture = new Fixture();
        applyOriginalPlayer(fixture);
        fixture.timer.setElapsedMs(TIMER_MS);

        fixture.manager.save(fixture.context, fixture.scenario);

        // Same seed, other dimension: still a world mismatch.
        fixture.harness.worlds().resetPracticeWorld(fixture.world, SEED,
                WorldAdapter.PracticeWorldOptions.builder(PracticeDimension.END).build());
        fixture.harness.players().teleport(fixture.player,
                new PracticePosition(8.5, 70.0, 8.5, 0.0f, 0.0f));

        fixture.manager.restore(fixture.context, fixture.scenario);

        assertEquals(SEED, fixture.context.world().seed());
        assertEquals(PracticeDimension.OVERWORLD, fixture.context.world().dimension());
        assertPlayerMatchesOriginal(fixture);
    }

    private static void applyOriginalPlayer(Fixture fixture) throws PracticeException {
        fixture.harness.players().teleport(fixture.player, SPAWN);
        fixture.harness.players().setHealth(fixture.player, HEALTH);
        fixture.harness.players().setFood(fixture.player, FOOD);
        fixture.harness.setSaturation("p1", SATURATION);
        fixture.harness.setXp("p1", XP_LEVEL, XP_POINTS);
        fixture.harness.setEffects("p1", EFFECTS);
        fixture.harness.setSelectedSlot("p1", SLOT);
        fixture.harness.players().applyLoadout(fixture.player, originalLoadout());
    }

    private static void applyMutatedPlayer(Fixture fixture) throws PracticeException {
        fixture.harness.players().teleport(fixture.player,
                new PracticePosition(-300.0, 80.0, 44.0, -90.0f, 10.0f));
        fixture.harness.players().setHealth(fixture.player, 2.0);
        fixture.harness.players().setFood(fixture.player, 3);
        fixture.harness.setSaturation("p1", 0.0f);
        fixture.harness.setXp("p1", 0, 0);
        fixture.harness.setEffects("p1", Arrays.asList("minecraft:poison"));
        fixture.harness.setSelectedSlot("p1", 0);
        fixture.harness.players().applyLoadout(fixture.player, new Loadout("mutated",
                Arrays.asList(new Loadout.Item("minecraft:dirt", 0, 1))));
    }

    private static void assertPlayerMatchesOriginal(Fixture fixture) throws PracticeException {
        PracticePosition position = fixture.harness.positionOf("p1");
        assertEquals(SPAWN.x(), position.x(), 0.0);
        assertEquals(SPAWN.y(), position.y(), 0.0);
        assertEquals(SPAWN.z(), position.z(), 0.0);
        assertEquals(SPAWN.yaw(), position.yaw(), 0.0f);
        assertEquals(SPAWN.pitch(), position.pitch(), 0.0f);
        assertEquals(HEALTH, fixture.harness.players().getHealth(fixture.player), 0.0);
        assertEquals(FOOD, fixture.harness.players().getFood(fixture.player));
        assertEquals(SATURATION, fixture.harness.saturationOf("p1"), 0.0f);
        assertEquals(XP_LEVEL, fixture.harness.xpLevelOf("p1"));
        assertEquals(EFFECTS, fixture.harness.effectsOf("p1"));
        assertEquals(SLOT, fixture.harness.selectedSlotOf("p1"));
        // XP points are only observable through a fresh snapshot.
        PracticeCheckpoint.PlayerSnapshot check =
                fixture.harness.players().capturePlayerState(fixture.player);
        assertEquals(XP_POINTS, check.xpPoints());
        assertLoadoutEquals(originalLoadout(), fixture.harness.appliedLoadout("p1"));
        assertLoadoutEquals(originalLoadout(), check.inventory());
    }

    private static Loadout originalLoadout() {
        List<Loadout.Item> items = new ArrayList<Loadout.Item>();
        items.add(new Loadout.Item("minecraft:diamond_pickaxe", 0, 1));
        items.add(new Loadout.Item("minecraft:oak_planks", 9, 64));
        items.add(new Loadout.Item("minecraft:diamond_boots", 100, 1));
        items.add(new Loadout.Item("minecraft:diamond_leggings", 101, 1));
        items.add(new Loadout.Item("minecraft:diamond_chestplate", 102, 1));
        items.add(new Loadout.Item("minecraft:diamond_helmet", 103, 1));
        items.add(new Loadout.Item("minecraft:shield", -106, 1));
        return new Loadout("checkpoint", items);
    }

    private static void assertLoadoutEquals(Loadout expected, Loadout actual) {
        assertTrue("expected a restored loadout", actual != null);
        assertEquals(expected.items().size(), actual.items().size());
        for (int i = 0; i < expected.items().size(); i++) {
            assertEquals(expected.items().get(i).itemId(), actual.items().get(i).itemId());
            assertEquals(expected.items().get(i).slot(), actual.items().get(i).slot());
            assertEquals(expected.items().get(i).count(), actual.items().get(i).count());
        }
    }

    private static Map<String, String> originalScenarioState() {
        Map<String, String> state = new LinkedHashMap<String, String>();
        state.put("target", "minecraft:village_plains");
        state.put("entered", "true");
        state.put("progress", "3");
        return state;
    }

    private static String captureScenarioType(Fixture fixture) throws PracticeException {
        return fixture.scenario.captureState(fixture.context).type();
    }
}
