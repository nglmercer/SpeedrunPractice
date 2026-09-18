package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.CommandAdapter;
import com.gregor0410.speedrunpractice.common.adapter.GuiAdapter;
import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeResult;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.seeds.SeedResult;
import com.gregor0410.speedrunpractice.common.seeds.SeedSearchTask;
import com.gregor0410.speedrunpractice.testsupport.ScenarioTestHarness;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PracticeRuntimeTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static PracticeRuntime runtime(Path configDir, MinecraftAdapter adapter) {
        return SpeedrunPracticeBootstrap.create(adapter, configDir);
    }

    private static PracticeSettings settings(String key, String value) {
        PracticeSettings settings = new PracticeSettings();
        settings.set(key, value);
        return settings;
    }

    @Test
    public void bootstrapCreatesEmptyRuntime() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), harness);

        assertSame(harness, runtime.adapter());
        assertNotNull(runtime.engine());
        assertNotNull(runtime.seedStore());
        assertNotNull(runtime.loadouts());
        assertNotNull(runtime.checkpoints());
        assertNotNull(runtime.timer());
        assertNotNull(runtime.statistics());
        assertNotNull(runtime.config());
        assertTrue(runtime.customScenarios().isEmpty());
        assertTrue(runtime.loadouts().list().isEmpty());
        assertFalse(runtime.hasActive());
        assertFalse(runtime.currentSeed().isPresent());
        assertNull(runtime.activeSearch());
        assertNull(runtime.searchProgress());
    }

    @Test
    public void bootstrapRejectsNulls() throws Exception {
        try {
            SpeedrunPracticeBootstrap.create(null, folder.getRoot().toPath());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            SpeedrunPracticeBootstrap.create(new ScenarioTestHarness(), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void bootstrapLoadsCustomScenariosAndLoadouts() throws Exception {
        Path root = folder.getRoot().toPath();
        Path scenarios = root.resolve("scenarios");
        Files.createDirectories(scenarios);
        Files.write(scenarios.resolve("custom_end.json"),
                ("{\"id\": \"custom_end\", \"type\": \"end\","
                        + " \"seed\": {\"source\": \"fixed\", \"value\": 99}}").getBytes(StandardCharsets.UTF_8));
        Files.write(scenarios.resolve("broken.json"), "{nope".getBytes(StandardCharsets.UTF_8));
        Path loadouts = root.resolve("loadouts");
        Files.createDirectories(loadouts);
        Files.write(loadouts.resolve("test_pack.json"),
                ("{\"id\": \"test_pack\", \"items\": ["
                        + "{\"item\": \"minecraft:stone\", \"slot\": 0, \"count\": 1}]}")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(loadouts.resolve("broken.json"), "{nope".getBytes(StandardCharsets.UTF_8));

        PracticeRuntime runtime = runtime(root, new ScenarioTestHarness());
        assertTrue(runtime.customScenarios().containsKey("custom_end"));
        assertNotNull(runtime.loadouts().get("test_pack"));

        // Broken files are skipped without breaking startup; the fixed seed
        // from the definition drives seed selection.
        PracticeSession session = runtime.startCustom("custom_end", new PracticeSettings(),
                ScenarioTestHarness.player("p1"));
        assertEquals(99L, session.seed());
        assertEquals(PracticeType.END, runtime.engine().currentScenario().type());
        runtime.stop();
    }

    @Test
    public void startCustomUnknownIdFailsReadably() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        try {
            runtime.startCustom("missing", new PracticeSettings(), ScenarioTestHarness.player("p1"));
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("missing"));
        }
    }

    @Test
    public void fixedSeedSourceWithoutValueFailsReadably() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        try {
            runtime.startPractice(PracticeType.END, settings("seed.source", "fixed"),
                    ScenarioTestHarness.player("p1"));
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("seed.value"));
        }
        assertFalse(runtime.hasActive());
    }

    @Test
    public void fixedSeedSourceUsesSettingsValue() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        PracticeSettings settings = new PracticeSettings();
        settings.set("seed.source", "fixed");
        settings.set("seed.value", "123");
        PracticeSession session = runtime.startPractice(PracticeType.END, settings,
                ScenarioTestHarness.player("p1"));
        assertEquals(123L, session.seed());
        assertEquals(123L, runtime.currentSeed().getAsLong());
        runtime.stop();
    }

    @Test
    public void unknownSeedSourceFailsReadably() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        try {
            runtime.startPractice(PracticeType.END, settings("seed.source", "bogus"),
                    ScenarioTestHarness.player("p1"));
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("bogus"));
        }
        assertFalse(runtime.hasActive());
    }

    @Test
    public void customTypeNeedsStartCustom() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        try {
            runtime.startPractice(PracticeType.CUSTOM, new PracticeSettings(),
                    ScenarioTestHarness.player("p1"), 1L);
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("scenario file"));
        }
    }

    @Test
    public void endCompletesThroughRuntime() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), harness);
        PracticeSession session = runtime.startPractice(PracticeType.END, new PracticeSettings(),
                ScenarioTestHarness.player("p1"), 3L);
        assertEquals(PracticeState.RUNNING, session.state());
        assertEquals(100.0, harness.positionOf("p1").x(), 0.0);
        assertFalse(runtime.tick().isFinished());
        harness.setLivingDragon(runtime.engine().currentContext().world().handleId(), false);
        assertTrue(runtime.tick().isFinished());
        assertEquals(1, runtime.statistics().completed(PracticeId.of("end")));
        assertTrue(runtime.statistics().personalBest(PracticeId.of("end")).isPresent());
        runtime.stop();
        assertFalse(runtime.hasActive());
    }

    @Test
    public void seedListSourceSurvivesResets() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        runtime.seedStore().writeImport("seed-list", Arrays.asList("5", "6"));
        PracticePlayer player = ScenarioTestHarness.player("p1");

        PracticeSession session = runtime.startPractice(PracticeType.OVERWORLD,
                settings("seed.source", "list"), player);
        assertEquals(5L, session.seed());
        runtime.reset(PracticeScenario.ResetMode.NEW_SEED);
        assertEquals(6L, runtime.currentSeed().getAsLong());
        runtime.reset(PracticeScenario.ResetMode.PREVIOUS_SEED);
        assertEquals(5L, runtime.currentSeed().getAsLong());
        runtime.reset(PracticeScenario.ResetMode.SAME_SEED);
        assertEquals(5L, runtime.currentSeed().getAsLong());
        runtime.restartOnSeed(777L);
        assertEquals(777L, runtime.currentSeed().getAsLong());
        runtime.stop();
    }

    @Test
    public void checkpointRoundTripThroughRuntime() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), harness);
        PracticePlayer player = ScenarioTestHarness.player("p1");
        runtime.startPractice(PracticeType.OVERWORLD, new PracticeSettings(), player, 11L);
        runtime.saveCheckpoint();
        assertTrue(runtime.hasCheckpoint());
        harness.players().teleport(player, new PracticePosition(999.0, 70.0, 999.0));
        runtime.restoreCheckpoint();
        assertEquals(0.0, harness.positionOf("p1").x(), 0.0);
        runtime.clearCheckpoint();
        assertFalse(runtime.hasCheckpoint());
        runtime.stop();
    }

    @Test
    public void idleOperationsFailReadablyOrStayLenient() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        try {
            runtime.tick();
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("No practice is running"));
        }
        try {
            runtime.reset(PracticeScenario.ResetMode.SAME_SEED);
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("No practice is running"));
        }
        try {
            runtime.restartOnSeed(1L);
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("No practice is running"));
        }
        try {
            runtime.saveCheckpoint();
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("No practice is running"));
        }
        try {
            runtime.restoreCheckpoint();
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("No practice is running"));
        }
        try {
            runtime.favoriteCurrentSeed();
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("No practice is running"));
        }
        // Lenient when idle.
        runtime.stop();
        runtime.clearCheckpoint();
        assertFalse(runtime.hasCheckpoint());
        assertFalse(runtime.cancelSearch());
        runtime.shutdown();
    }

    @Test
    public void favoriteCurrentSeedPersists() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        runtime.startPractice(PracticeType.END, new PracticeSettings(), ScenarioTestHarness.player("p1"), 42L);
        assertEquals(42L, runtime.favoriteCurrentSeed());
        assertTrue(runtime.seedStore().listFavoriteSeeds().contains(42L));
        runtime.stop();
    }

    @Test
    public void searchTrackingCancelsPrevious() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        FakeSearch first = new FakeSearch();
        FakeSearch second = new FakeSearch();
        runtime.startSearch(first);
        assertSame(first, runtime.activeSearch());
        runtime.startSearch(second);
        assertTrue(first.cancelled);
        assertSame(second, runtime.activeSearch());
        assertNotNull(runtime.searchProgress());
        assertTrue(runtime.cancelSearch());
        assertTrue(second.cancelled);
        assertNull(runtime.activeSearch());
        assertFalse(runtime.cancelSearch());
    }

    @Test
    public void guiActionsDelegateAndGuardAvailability() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), harness);
        PracticePlayer player = ScenarioTestHarness.player("p1");
        runtime.openMainMenu(player);
        assertTrue(harness.guiCalls().contains("mainMenu"));

        PracticeRuntime noGui = runtime(folder.getRoot().toPath(), guiUnavailable(harness));
        try {
            noGui.openMainMenu(player);
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("Commands still work"));
        }
    }

    @Test
    public void persistLoadoutsRoundTrip() throws Exception {
        Path root = folder.getRoot().toPath();
        PracticeRuntime runtime = runtime(root, new ScenarioTestHarness());
        runtime.loadouts().save(new com.gregor0410.speedrunpractice.common.loadout.Loadout(
                "roundtrip", Collections.<com.gregor0410.speedrunpractice.common.loadout.Loadout.Item>emptyList()));
        runtime.persistLoadouts();
        assertTrue(Files.exists(root.resolve("loadouts").resolve("roundtrip.json")));

        PracticeRuntime reloaded = runtime(root, new ScenarioTestHarness());
        assertNotNull(reloaded.loadouts().get("roundtrip"));
    }

    @Test
    public void reloadSummarizesDiskState() throws Exception {
        Path root = folder.getRoot().toPath();
        PracticeRuntime runtime = runtime(root, new ScenarioTestHarness());
        String summary = runtime.reload();
        assertTrue(summary.contains("0 custom scenarios"));
        assertTrue(summary.contains("0 loadouts"));
    }

    @Test
    public void shutdownStopsPracticeAndSearch() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        runtime.startPractice(PracticeType.END, new PracticeSettings(), ScenarioTestHarness.player("p1"), 9L);
        FakeSearch search = new FakeSearch();
        runtime.startSearch(search);
        runtime.shutdown();
        assertFalse(runtime.hasActive());
        assertTrue(search.cancelled);
        assertNull(runtime.activeSearch());
    }

    @Test
    public void executorStartStopRestart() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), harness);
        CommandAdapter.CommandExecutor executor = runtime.asCommandExecutor();
        PracticePlayer player = ScenarioTestHarness.player("p1");

        FakeCommandContext start = new FakeCommandContext("start", player);
        start.stringArgs.put("type", "end");
        assertEquals(1, executor.execute(start));
        assertTrue(runtime.hasActive());
        assertTrue(start.feedback.get(0).contains("Started End on seed"));
        long seed = runtime.currentSeed().getAsLong();

        FakeCommandContext same = new FakeCommandContext("restart.same", player);
        assertEquals(1, executor.execute(same));
        assertEquals(seed, runtime.currentSeed().getAsLong());

        FakeCommandContext next = new FakeCommandContext("seed.next", player);
        assertEquals(1, executor.execute(next));

        FakeCommandContext previous = new FakeCommandContext("seed.previous", player);
        assertEquals(1, executor.execute(previous));
        assertEquals(seed, runtime.currentSeed().getAsLong());

        FakeCommandContext set = new FakeCommandContext("seed.set", player);
        set.longArgs.put("seed", 777L);
        assertEquals(1, executor.execute(set));
        assertEquals(777L, runtime.currentSeed().getAsLong());

        FakeCommandContext favorite = new FakeCommandContext("seed.favorite", player);
        assertEquals(1, executor.execute(favorite));
        assertTrue(runtime.seedStore().listFavoriteSeeds().contains(777L));

        FakeCommandContext stop = new FakeCommandContext("stop", player);
        assertEquals(1, executor.execute(stop));
        assertFalse(runtime.hasActive());
    }

    @Test
    public void executorRejectsUnknownType() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        FakeCommandContext start = new FakeCommandContext("start", ScenarioTestHarness.player("p1"));
        start.stringArgs.put("type", "bogus");
        try {
            runtime.asCommandExecutor().execute(start);
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("bogus"));
        }
    }

    @Test
    public void executorCheckpointAndStats() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), harness);
        CommandAdapter.CommandExecutor executor = runtime.asCommandExecutor();
        PracticePlayer player = ScenarioTestHarness.player("p1");
        runtime.startPractice(PracticeType.OVERWORLD, new PracticeSettings(), player, 11L);

        assertEquals(1, executor.execute(new FakeCommandContext("checkpoint.save", player)));
        assertEquals(1, executor.execute(new FakeCommandContext("checkpoint.load", player)));
        assertEquals(1, executor.execute(new FakeCommandContext("checkpoint.clear", player)));

        FakeCommandContext stats = new FakeCommandContext("stats.show", player);
        stats.stringArgs.put("practice", "overworld");
        assertEquals(1, executor.execute(stats));
        assertTrue(stats.feedback.get(0).contains("overworld:"));

        FakeCommandContext current = new FakeCommandContext("stats.show", player);
        assertEquals(1, executor.execute(current));
        assertTrue(current.feedback.get(0).contains("overworld:"));

        assertEquals(1, executor.execute(new FakeCommandContext("stats.reset", player)));
        assertEquals(0, runtime.statistics().attempts(PracticeId.of("overworld")));

        assertEquals(1, executor.execute(new FakeCommandContext("config.reload", player)));
        runtime.stop();
    }

    @Test
    public void executorLoadouts() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        CommandAdapter.CommandExecutor executor = runtime.asCommandExecutor();
        PracticePlayer player = ScenarioTestHarness.player("p1");

        FakeCommandContext save = new FakeCommandContext("loadout.save", player);
        save.stringArgs.put("name", "pack");
        assertEquals(1, executor.execute(save));

        FakeCommandContext list = new FakeCommandContext("loadout.list", player);
        assertEquals(1, executor.execute(list));
        assertTrue(list.feedback.get(0).contains("pack"));

        FakeCommandContext apply = new FakeCommandContext("loadout.apply", player);
        apply.stringArgs.put("name", "pack");
        assertEquals(1, executor.execute(apply));

        FakeCommandContext missing = new FakeCommandContext("loadout.apply", player);
        missing.stringArgs.put("name", "missing");
        try {
            executor.execute(missing);
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("missing"));
        }

        FakeCommandContext delete = new FakeCommandContext("loadout.delete", player);
        delete.stringArgs.put("name", "pack");
        assertEquals(1, executor.execute(delete));
        assertNull(runtime.loadouts().get("pack"));
    }

    @Test
    public void executorSeedSearchReporting() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        CommandAdapter.CommandExecutor executor = runtime.asCommandExecutor();
        PracticePlayer player = ScenarioTestHarness.player("p1");

        FakeCommandContext empty = new FakeCommandContext("seeds.results", player);
        assertEquals(1, executor.execute(empty));
        assertTrue(empty.feedback.get(0).contains("No seed search has been started"));

        FakeSearch search = new FakeSearch();
        search.tested = 10L;
        search.results.add(new SeedResult(5L, GameVersion.MC_1_16_1, null, null,
                SeedResult.VerificationState.UNVERIFIED, 0L));
        runtime.startSearch(search);
        FakeCommandContext results = new FakeCommandContext("seeds.results", player);
        assertEquals(1, executor.execute(results));
        assertTrue(results.feedback.get(0).contains("10 tested"));
        assertTrue(results.feedback.get(0).contains(" 5"));

        FakeCommandContext cancel = new FakeCommandContext("seeds.cancel", player);
        assertEquals(1, executor.execute(cancel));
        assertTrue(search.cancelled);

        FakeCommandContext missing = new FakeCommandContext("seeds.search", player);
        missing.stringArgs.put("preset", "missing");
        try {
            executor.execute(missing);
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("missing"));
        }
        runtime.seedStore().saveSearch("saved", "{}");
        FakeCommandContext saved = new FakeCommandContext("seeds.search", player);
        saved.stringArgs.put("preset", "saved");
        assertEquals(1, executor.execute(saved));
        assertTrue(saved.feedback.get(0).contains("saved"));
        assertNotNull(runtime.activeSearch());

        FakeCommandContext importHelp = new FakeCommandContext("seeds.import", player);
        assertEquals(1, executor.execute(importHelp));
        assertTrue(importHelp.feedback.get(0).contains("seed.list"));

        assertEquals(1, executor.execute(new FakeCommandContext("seeds.cancel", player)));
        try {
            executor.execute(new FakeCommandContext("seeds.export", player));
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("No seed search"));
        }
        runtime.shutdown();
    }

    @Test
    public void executorLegacyAndUnknown() throws Exception {
        PracticeRuntime runtime = runtime(folder.getRoot().toPath(), new ScenarioTestHarness());
        CommandAdapter.CommandExecutor executor = runtime.asCommandExecutor();
        PracticePlayer player = ScenarioTestHarness.player("p1");

        FakeCommandContext legacy = new FakeCommandContext("legacy.end", player);
        assertEquals(1, executor.execute(legacy));
        assertTrue(legacy.feedback.get(0).contains("legacy"));

        try {
            executor.execute(new FakeCommandContext("bogus.action", player));
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("Unknown command"));
        }
        try {
            executor.execute(new FakeCommandContext(null, player));
            fail("expected PracticeException");
        } catch (PracticeException failure) {
            assertTrue(failure.getUserMessage().contains("Unknown command"));
        }
    }

    private static MinecraftAdapter guiUnavailable(final ScenarioTestHarness harness) {
        final GuiAdapter stub = new GuiAdapter() {
            @Override
            public void openMainMenu(PracticePlayer player) throws PracticeException {
                throw new PracticeException("unavailable");
            }

            @Override
            public void openScenarioScreen(PracticePlayer player, PracticePreset preset) throws PracticeException {
                throw new PracticeException("unavailable");
            }

            @Override
            public void openResultsScreen(PracticePlayer player, PracticeResult result) throws PracticeException {
                throw new PracticeException("unavailable");
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
        return (MinecraftAdapter) Proxy.newProxyInstance(PracticeRuntimeTest.class.getClassLoader(),
                new Class<?>[] { MinecraftAdapter.class }, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("gui")) {
                            return stub;
                        }
                        return method.invoke(harness, args);
                    }
                });
    }

    private static final class FakeSearch implements SeedSearchTask {
        private final List<SeedResult> results = new ArrayList<SeedResult>();
        private long tested;
        private boolean cancelled;
        private boolean finished;

        @Override
        public SearchProgress progress() {
            return new SearchProgress(tested, results.size(), cancelled, finished);
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public List<SeedResult> results() {
            return Collections.unmodifiableList(new ArrayList<SeedResult>(results));
        }
    }

    private static final class FakeCommandContext implements CommandAdapter.CommandContextView {
        private final String action;
        private final PracticePlayer player;
        private final Map<String, String> stringArgs = new HashMap<String, String>();
        private final Map<String, Long> longArgs = new HashMap<String, Long>();
        private final List<String> feedback = new ArrayList<String>();

        private FakeCommandContext(String action, PracticePlayer player) {
            this.action = action;
            this.player = player;
        }

        @Override
        public String action() {
            return action;
        }

        @Override
        public PracticePlayer player() {
            return player;
        }

        @Override
        public boolean hasArg(String name) {
            return stringArgs.containsKey(name) || longArgs.containsKey(name);
        }

        @Override
        public String stringArg(String name) {
            return stringArgs.get(name);
        }

        @Override
        public long longArg(String name) {
            Long value = longArgs.get(name);
            return value == null ? 0L : value.longValue();
        }

        @Override
        public int intArg(String name) {
            return 0;
        }

        @Override
        public void feedback(String message) {
            feedback.add(message);
        }
    }
}
