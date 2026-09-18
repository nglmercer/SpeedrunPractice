package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.CommandAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.seeds.SeedSearchTask;
import com.gregor0410.speedrunpractice.seedsearch.CancellableSeedSearch;
import com.gregor0410.speedrunpractice.testsupport.ScenarioTestHarness;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** End-to-end command coverage for seed search, results and export. */
public class SeedSearchExecutorTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void searchFindsResultsAndExports() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        Map<String, Object> findings = new LinkedHashMap<String, Object>();
        findings.put("structure.village.distance", 100L);
        harness.setAnalyzer(matchingAnalyzer(findings));
        PracticeRuntime runtime = SpeedrunPracticeBootstrap.create(harness, folder.getRoot().toPath());
        runtime.seedStore().saveSearch("nearby",
                "{\"structures\": {\"village\": 1500}, \"startSeed\": 100, "
                        + "\"maxResults\": 2, \"maxAttempts\": 10}");

        FakeCommandContext search = context("seeds.search", "preset", "nearby");
        assertEquals(1, runtime.asCommandExecutor().execute(search));
        assertTrue(search.feedback.get(0).contains("nearby"));

        SeedSearchTask task = runtime.activeSearch();
        assertNotNull(task);
        assertTrue(((CancellableSeedSearch) task).awaitCompletion(5000));
        assertEquals(2, task.results().size());
        assertEquals(100L, task.results().get(0).seed());
        assertTrue(task.results().get(0).matchedFilters().contains("structure:village:0-1500"));

        FakeCommandContext results = context("seeds.results", null, null);
        assertEquals(1, runtime.asCommandExecutor().execute(results));
        assertTrue(results.feedback.get(0).contains("2 matched"));

        FakeCommandContext export = context("seeds.export", null, null);
        assertEquals(1, runtime.asCommandExecutor().execute(export));
        assertTrue(export.feedback.get(0).contains("Exported 2 seeds"));
        assertEquals(1, runtime.seedStore().listExports().size());
        runtime.shutdown();
    }

    @Test
    public void unknownPresetFailsReadably() throws Exception {
        PracticeRuntime runtime = runtime();
        try {
            runtime.asCommandExecutor().execute(context("seeds.search", "preset", "missing"));
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("missing"));
        }
        runtime.shutdown();
    }

    @Test
    public void lavaPresetFailsFast() throws Exception {
        PracticeRuntime runtime = runtime();
        runtime.seedStore().saveSearch("lava", "{\"lava\": true}");
        try {
            runtime.asCommandExecutor().execute(context("seeds.search", "preset", "lava"));
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("lava"));
        }
        assertNull(runtime.activeSearch());
        runtime.shutdown();
    }

    @Test
    public void versionMismatchFailsReadably() throws Exception {
        PracticeRuntime runtime = runtime();
        runtime.seedStore().saveSearch("other", "{\"version\": \"26.3\"}");
        try {
            runtime.asCommandExecutor().execute(context("seeds.search", "preset", "other"));
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("26.3"));
        }
        assertNull(runtime.activeSearch());
        runtime.shutdown();
    }

    @Test
    public void startPresetSearchRunsWithoutCommands() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        Map<String, Object> findings = new LinkedHashMap<String, Object>();
        findings.put("structure.village.distance", 100L);
        harness.setAnalyzer(matchingAnalyzer(findings));
        PracticeRuntime runtime = SpeedrunPracticeBootstrap.create(harness, folder.getRoot().toPath());
        runtime.seedStore().saveSearch("nearby",
                "{\"structures\": {\"village\": 1500}, \"startSeed\": 100, "
                        + "\"maxResults\": 1, \"maxAttempts\": 10}");
        String message = runtime.startPresetSearch("nearby");
        assertTrue(message.contains("nearby"));
        assertNotNull(runtime.activeSearch());
        assertTrue(((CancellableSeedSearch) runtime.activeSearch()).awaitCompletion(5000));
        assertEquals(1, runtime.activeSearch().results().size());
        runtime.shutdown();
    }

    @Test
    public void startPresetSearchRejectsEmptyName() throws Exception {
        PracticeRuntime runtime = runtime();
        try {
            runtime.startPresetSearch("  ");
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("Pick a search preset"));
        }
        runtime.shutdown();
    }

    @Test
    public void startPresetSearchLavaDependsOnAnalyzer() throws Exception {
        PracticeRuntime runtime = runtime();
        runtime.seedStore().saveSearch("lava", "{\"lava\": true}");
        try {
            runtime.startPresetSearch("lava");
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("lava"));
        }
        assertNull(runtime.activeSearch());
        runtime.shutdown();

        ScenarioTestHarness lavaHarness = new ScenarioTestHarness();
        lavaHarness.setAnalyzer(new SeedAnalyzer() {
            @Override
            public SeedAnalysis analyze(long seed, SeedQuery query) {
                return SeedAnalysis.mismatch();
            }

            @Override
            public boolean matches(long seed, SeedQuery query) {
                return false;
            }

            @Override
            public boolean supportsLava() {
                return true;
            }
        });
        PracticeRuntime lavaRuntime =
                SpeedrunPracticeBootstrap.create(lavaHarness, folder.getRoot().toPath());
        lavaRuntime.seedStore().saveSearch("lava", "{\"lava\": true}");
        String message = lavaRuntime.startPresetSearch("lava");
        assertTrue(message.contains("lava"));
        assertNotNull(lavaRuntime.activeSearch());
        assertTrue(((CancellableSeedSearch) lavaRuntime.activeSearch()).awaitCompletion(5000));
        lavaRuntime.shutdown();
    }

    @Test
    public void exportWithoutSearchFailsReadably() throws Exception {
        PracticeRuntime runtime = runtime();
        try {
            runtime.asCommandExecutor().execute(context("seeds.export", null, null));
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("No seed search"));
        }
        runtime.shutdown();
    }

    @Test
    public void exportWithoutResultsFailsReadably() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        harness.setAnalyzer(new SeedAnalyzer() {
            @Override
            public SeedAnalysis analyze(long seed, SeedQuery query) {
                return SeedAnalysis.mismatch();
            }

            @Override
            public boolean matches(long seed, SeedQuery query) {
                return false;
            }
        });
        PracticeRuntime runtime = SpeedrunPracticeBootstrap.create(harness, folder.getRoot().toPath());
        runtime.seedStore().saveSearch("none",
                "{\"startSeed\": 1, \"maxResults\": 1, \"maxAttempts\": 5}");
        assertEquals(1, runtime.asCommandExecutor().execute(context("seeds.search", "preset", "none")));
        assertTrue(((CancellableSeedSearch) runtime.activeSearch()).awaitCompletion(5000));
        try {
            runtime.asCommandExecutor().execute(context("seeds.export", null, null));
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("no results"));
        }
        runtime.shutdown();
    }

    @Test
    public void scenarioSeedFiltersReachTheQuery() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        final AtomicReference<SeedQuery> seen = new AtomicReference<SeedQuery>();
        harness.setAnalyzer(new SeedAnalyzer() {
            @Override
            public SeedAnalysis analyze(long seed, SeedQuery query) {
                seen.set(query);
                return SeedAnalysis.of(true, null);
            }

            @Override
            public boolean matches(long seed, SeedQuery query) {
                seen.set(query);
                return true;
            }
        });
        PracticeRuntime runtime = SpeedrunPracticeBootstrap.create(harness, folder.getRoot().toPath());
        PracticeSettings settings = new PracticeSettings();
        settings.set("seed.source", "search");
        settings.set("seed.filters",
                "biome=minecraft:plains,structure=minecraft:village:1500,"
                        + "bastionType=treasure,strongholdRing=2,lava=false");
        PracticeSession session =
                runtime.startPractice(PracticeType.OVERWORLD, settings, ScenarioTestHarness.player("p1"));
        assertNotNull(session);
        SeedQuery query = seen.get();
        assertNotNull(query);
        assertEquals("minecraft:plains", query.requiredBiome());
        assertEquals(Integer.valueOf(1500), query.requiredStructures().get("minecraft:village"));
        assertEquals("treasure", query.bastionType());
        assertEquals(2, query.strongholdRing());
        runtime.shutdown();
    }

    @Test
    public void badScenarioSeedFilterFailsReadably() throws Exception {
        PracticeRuntime runtime = runtime();
        PracticeSettings settings = new PracticeSettings();
        settings.set("seed.source", "search");
        settings.set("seed.filters", "biome");
        try {
            runtime.startPractice(PracticeType.OVERWORLD, settings, ScenarioTestHarness.player("p1"));
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("seed filter"));
        }
        runtime.shutdown();
    }

    private PracticeRuntime runtime() {
        return SpeedrunPracticeBootstrap.create(new ScenarioTestHarness(), folder.getRoot().toPath());
    }

    private static SeedAnalyzer matchingAnalyzer(final Map<String, Object> findings) {
        return new SeedAnalyzer() {
            @Override
            public SeedAnalysis analyze(long seed, SeedQuery query) {
                return SeedAnalysis.of(true, findings);
            }

            @Override
            public boolean matches(long seed, SeedQuery query) {
                return true;
            }
        };
    }

    private static FakeCommandContext context(String action, String argName, String argValue) {
        FakeCommandContext context =
                new FakeCommandContext(action, ScenarioTestHarness.player("p1"));
        if (argName != null) {
            context.stringArgs.put(argName, argValue);
        }
        return context;
    }

    private static final class FakeCommandContext implements CommandAdapter.CommandContextView {
        private final String action;
        private final PracticePlayer player;
        private final Map<String, String> stringArgs = new HashMap<String, String>();
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
            return stringArgs.containsKey(name);
        }

        @Override
        public String stringArg(String name) {
            return stringArgs.get(name);
        }

        @Override
        public long longArg(String name) {
            return 0L;
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
