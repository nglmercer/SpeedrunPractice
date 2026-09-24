package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import com.gregor0410.speedrunpractice.practices.SpeedrunPracticeBootstrap;
import com.gregor0410.speedrunpractice.testsupport.ScenarioTestHarness;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResetContractSuiteTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void fullResetLadderPassesWithNoLeaks() {
        ScenarioTestHarness harness = new ScenarioTestHarness(GameVersion.MC_1_21_1);
        PracticeRuntime runtime = SpeedrunPracticeBootstrap.create(harness, folder.getRoot().toPath());
        ResetContractSuite.Result result = ResetContractSuite.run(runtime,
                ScenarioTestHarness.player("p1"), countOf(harness));

        assertEquals("evidence: " + result.evidence(), result.total(), result.matches());
        assertEquals(6, result.total());
        assertTrue("evidence: " + result.evidence(), result.evidence().contains("reset.start"));
        assertTrue("evidence: " + result.evidence(), result.evidence().contains("reset.same_seed"));
        assertTrue("evidence: " + result.evidence(), result.evidence().contains("reset.new_seed"));
        assertTrue("evidence: " + result.evidence(), result.evidence().contains("reset.previous_seed"));
        assertTrue("evidence: " + result.evidence(), result.evidence().contains("reset.restart_seed"));
        assertTrue("evidence: " + result.evidence(), result.evidence().contains("reset.cleanup"));
        assertEquals(0, harness.worldCount());
    }

    @Test
    public void nullInputsFailReadably() {
        ScenarioTestHarness harness = new ScenarioTestHarness();
        PracticeRuntime runtime = SpeedrunPracticeBootstrap.create(harness, folder.getRoot().toPath());

        ResetContractSuite.Result noRuntime = ResetContractSuite.run(null,
                ScenarioTestHarness.player("p1"), countOf(harness));
        assertEquals(0, noRuntime.matches());
        assertEquals(1, noRuntime.total());

        ResetContractSuite.Result noPlayer = ResetContractSuite.run(runtime, null, countOf(harness));
        assertEquals(0, noPlayer.matches());

        ResetContractSuite.Result noLeakCheck = ResetContractSuite.run(runtime,
                ScenarioTestHarness.player("p1"), null);
        assertEquals(0, noLeakCheck.matches());
    }

    private static ResetContractSuite.LeakCheck countOf(final ScenarioTestHarness harness) {
        return new ResetContractSuite.LeakCheck() {
            @Override
            public int trackedCount() {
                return harness.worldCount();
            }
        };
    }
}
