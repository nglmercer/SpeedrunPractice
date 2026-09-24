package com.gregor0410.speedrunpractice.adapter263;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import com.gregor0410.speedrunpractice.practices.SpeedrunPracticeBootstrap;
import com.gregor0410.speedrunpractice.testsupport.ScenarioTestHarness;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Real-runtime reset ladder for 26.3 (no Minecraft needed): start on
 * seed A, {@code SAME_SEED} stays on A, {@code NEW_SEED} moves to B,
 * {@code PREVIOUS_SEED} returns to A, {@code restartOnSeed(C)} lands on C.
 * Every step keeps a valid RUNNING scenario on a freshly rebuilt world and
 * the old world is deleted (exactly one world alive, none after stop).
 */
public class RuntimeResetContractTest {
    private static final long SEED_A = 910001L;
    private static final long SEED_B = 910002L;
    private static final long SEED_C = 910003L;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void resetLadderRecyclesWorldsWithoutLeaks() throws Exception {
        ScenarioTestHarness harness = new ScenarioTestHarness(GameVersion.V_26_3);
        PracticeRuntime runtime = SpeedrunPracticeBootstrap.create(harness, folder.getRoot().toPath());
        PracticePlayer player = ScenarioTestHarness.player("p1");
        runtime.seedStore().writeImport("resets",
                Arrays.<String>asList(Long.toString(SEED_A), Long.toString(SEED_B)));
        PracticeSettings settings = new PracticeSettings();
        settings.set("seed.source", "imported");
        settings.set("seed.list", "resets");

        PracticeSession session = runtime.startPractice(PracticeType.OVERWORLD, settings, player);
        assertEquals(SEED_A, session.seed());
        String startHandle = checkRunning(runtime, harness, SEED_A, null);

        runtime.reset(PracticeScenario.ResetMode.SAME_SEED);
        String sameHandle = checkRunning(runtime, harness, SEED_A, startHandle);

        runtime.reset(PracticeScenario.ResetMode.NEW_SEED);
        assertTrue("new seed must differ from " + SEED_A,
                runtime.currentSeed().getAsLong() != SEED_A);
        String newHandle = checkRunning(runtime, harness, SEED_B, sameHandle);

        runtime.reset(PracticeScenario.ResetMode.PREVIOUS_SEED);
        String previousHandle = checkRunning(runtime, harness, SEED_A, newHandle);

        runtime.restartOnSeed(SEED_C);
        checkRunning(runtime, harness, SEED_C, previousHandle);

        assertEquals(GameVersion.V_26_3, runtime.adapter().version());
        runtime.stop();
        assertFalse(runtime.hasActive());
        assertEquals(0, harness.worldCount());
    }

    private static String checkRunning(PracticeRuntime runtime, ScenarioTestHarness harness,
                                       long expected, String previousHandle) {
        assertTrue(runtime.hasActive());
        // The session is re-read every time: restartOnSeed replaces it.
        PracticeSession session = runtime.engine().currentContext().session();
        assertEquals(PracticeState.RUNNING, session.state());
        assertEquals(expected, runtime.currentSeed().getAsLong());
        assertEquals(expected, session.seed());
        assertEquals(expected, runtime.engine().currentContext().world().seed());
        assertEquals(PracticeType.OVERWORLD, runtime.engine().currentScenario().type());
        assertEquals(1, harness.worldCount());
        String handle = runtime.engine().currentContext().world().handleId();
        if (previousHandle != null) {
            assertFalse("world handle " + handle + " was reused instead of rebuilt",
                    previousHandle.equals(handle));
        }
        return handle;
    }
}
