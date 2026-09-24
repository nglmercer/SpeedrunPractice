package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import com.gregor0410.speedrunpractice.practices.SpeedrunPracticeBootstrap;
import com.gregor0410.speedrunpractice.testsupport.ScenarioTestHarness;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless coverage for the event-bridge suite logic: a scripted driver
 * plays the poller's role (diff scripted game state, fire the matching
 * shared event through the real engine) so the suite's settings, polling
 * cadence and assertions are exercised without Minecraft.
 */
public class EventBridgeContractSuiteTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void allFiveTranslationsPass() {
        Fixture fixture = fixture();
        EventBridgeContractSuite.Result result = EventBridgeContractSuite.run(
                new ScriptedDriver(fixture.runtime, fixture.player, true), 12345L);

        assertEquals("evidence: " + result.evidence(), 5, result.total());
        assertEquals("evidence: " + result.evidence(), result.total(), result.matches());
        assertTrue("evidence: " + result.evidence(),
                result.evidence().contains("eventbridge.movement"));
        assertTrue("evidence: " + result.evidence(),
                result.evidence().contains("eventbridge.dim_change"));
        assertTrue("evidence: " + result.evidence(),
                result.evidence().contains("eventbridge.portal_exit"));
        assertTrue("evidence: " + result.evidence(),
                result.evidence().contains("eventbridge.dragon_death"));
        assertTrue("evidence: " + result.evidence(),
                result.evidence().contains("eventbridge.structure_reach"));
    }

    @Test
    public void missingStructureFailsOnlyItsCheck() {
        Fixture fixture = fixture();
        EventBridgeContractSuite.Result result = EventBridgeContractSuite.run(
                new ScriptedDriver(fixture.runtime, fixture.player, false), 12345L);

        assertEquals(5, result.total());
        assertEquals("evidence: " + result.evidence(), 4, result.matches());
        assertTrue("evidence: " + result.evidence(),
                result.evidence().contains("eventbridge.movement"));
    }

    @Test
    public void nullInputsFailReadably() {
        EventBridgeContractSuite.Result none =
                EventBridgeContractSuite.run(null, 12345L);
        assertEquals(0, none.matches());
        assertEquals(1, none.total());

        Fixture fixture = fixture();
        EventBridgeContractSuite.Result noRuntime = EventBridgeContractSuite.run(
                new ScriptedDriver(null, fixture.player, true), 12345L);
        assertEquals(0, noRuntime.matches());

        EventBridgeContractSuite.Result noPlayer = EventBridgeContractSuite.run(
                new ScriptedDriver(fixture.runtime, null, true), 12345L);
        assertEquals(0, noPlayer.matches());
    }

    private Fixture fixture() {
        ScenarioTestHarness harness = new ScenarioTestHarness(GameVersion.MC_1_21_1);
        PracticeRuntime runtime =
                SpeedrunPracticeBootstrap.create(harness, folder.getRoot().toPath());
        return new Fixture(runtime, ScenarioTestHarness.player("p1"));
    }

    private static final class Fixture {
        final PracticeRuntime runtime;
        final PracticePlayer player;

        private Fixture(PracticeRuntime runtime, PracticePlayer player) {
            this.runtime = runtime;
            this.player = player;
        }
    }

    /**
     * Poller stand-in: scripted game state with baseline latching per
     * session, translating diffs into the same shared events the real
     * version pollers emit.
     */
    private static final class ScriptedDriver implements EventBridgeContractSuite.Driver {
        private final PracticeRuntime runtime;
        private final PracticePlayer player;
        private final boolean structureAvailable;
        private PracticeSession session;
        private PracticePosition position = new PracticePosition(0, 64, 0);
        private PracticePosition lastPosition;
        private PracticeDimension dimension = PracticeDimension.OVERWORLD;
        private PracticeDimension lastDimension;
        private boolean dragonAlive;
        private boolean lastDragonAlive;
        private boolean inStructure;
        private boolean lastInStructure;

        private ScriptedDriver(PracticeRuntime runtime, PracticePlayer player,
                               boolean structureAvailable) {
            this.runtime = runtime;
            this.player = player;
            this.structureAvailable = structureAvailable;
        }

        @Override
        public PracticeRuntime runtime() {
            return runtime;
        }

        @Override
        public PracticePlayer player() {
            return player;
        }

        @Override
        public void poll() throws Exception {
            PracticeSession current = runtime.engine().currentContext().session();
            if (session != current) {
                // Fresh practice, fresh world: reset scripted state, then latch.
                session = current;
                position = new PracticePosition(0, 64, 0);
                dimension = PracticeDimension.OVERWORLD;
                dragonAlive = false;
                inStructure = false;
                lastPosition = position;
                lastDimension = dimension;
                lastDragonAlive = false;
                lastInStructure = false;
                return;
            }
            if (!sameBlock(lastPosition, position)) {
                runtime.fireEvent(new PracticeEvent.PlayerMovedEvent(lastPosition, position));
                lastPosition = position;
            }
            if (lastDimension != dimension) {
                PracticeDimension from = lastDimension;
                lastDimension = dimension;
                runtime.fireEvent(new PracticeEvent.DimensionChangedEvent(from, dimension));
                if (isNetherPortalTrip(from, dimension)) {
                    runtime.fireEvent(new PracticeEvent.PortalExitEvent(dimension, position));
                }
            }
            if (lastDragonAlive && !dragonAlive) {
                runtime.fireEvent(new PracticeEvent.DragonKilledEvent(
                        runtime.engine().currentContext().world().handleId()));
            }
            lastDragonAlive = dragonAlive;
            if (!lastInStructure && inStructure) {
                runtime.fireEvent(new PracticeEvent.StructureEnteredEvent("village", position));
                lastInStructure = true;
            }
        }

        @Override
        public void movePlayer(double dx, double dz) {
            position = new PracticePosition(position.x() + dx, position.y(), position.z() + dz);
        }

        @Override
        public void enterNether() {
            dimension = PracticeDimension.NETHER;
        }

        @Override
        public void spawnDragon() {
            dragonAlive = true;
        }

        @Override
        public void killDragon() {
            dragonAlive = false;
        }

        @Override
        public boolean hasLivingDragon() {
            return dragonAlive;
        }

        @Override
        public boolean enterPolledStructure() {
            if (!structureAvailable) {
                return false;
            }
            inStructure = true;
            return true;
        }

        private static boolean sameBlock(PracticePosition a, PracticePosition b) {
            return a.blockX() == b.blockX() && a.blockY() == b.blockY()
                    && a.blockZ() == b.blockZ();
        }

        private static boolean isNetherPortalTrip(PracticeDimension from, PracticeDimension to) {
            return (from == PracticeDimension.OVERWORLD && to == PracticeDimension.NETHER)
                    || (from == PracticeDimension.NETHER && to == PracticeDimension.OVERWORLD);
        }
    }
}
