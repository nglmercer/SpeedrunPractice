package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;

import java.util.ArrayList;
import java.util.List;

/**
 * Verification-only contract for the live event bridge: real game-state
 * mutation -&gt; version {@code EventPoller} -&gt; shared {@link
 * com.gregor0410.speedrunpractice.common.events.PracticeEvent} -&gt;
 * {@code ScenarioEngine} timer/scenario dispatch.
 *
 * <p>Unlike {@link ScenarioContractSuite}, which fires synthetic events, every
 * check here mutates real Minecraft state through the version {@link Driver},
 * runs the real poller, and observes the engine (timer running, session
 * completed). The suite never calls {@code runtime.tick()}, so a completion
 * can only arrive through the poller -&gt; {@code fireEvent} path.
 *
 * <p>Covered translations: movement, dimension change, Nether portal exit,
 * dragon death and structure reach. Overworld checks use
 * {@code overworld.goal=manual} so the scenario itself can never complete;
 * only the event under test moves the timer/session.
 */
public final class EventBridgeContractSuite {
    /**
     * Version-local bridge: owns one real {@code EventPoller} plus the
     * harness player, and performs real game-state mutations on the
     * practice worlds the suite starts.
     */
    public interface Driver {
        /** The armed shared runtime under test. */
        PracticeRuntime runtime();

        /** Live harness player the suite starts practices with. */
        PracticePlayer player();

        /** Runs exactly one real poller pass over the active practice. */
        void poll() throws Exception;

        /** Moves the live player by a real block offset. */
        void movePlayer(double dx, double dz) throws Exception;

        /** Moves the live player into the practice Nether (real dimension change). */
        void enterNether() throws Exception;

        /** Spawns one real living dragon in the active practice world. */
        void spawnDragon() throws Exception;

        /** Kills every living dragon in the active practice world. */
        void killDragon() throws Exception;

        /** True when the live dragon adapter currently sees a living dragon. */
        boolean hasLivingDragon() throws Exception;

        /**
         * Teleports the live player inside a poller-watched structure
         * (village). False when no candidate exists in range.
         */
        boolean enterPolledStructure() throws Exception;
    }

    private EventBridgeContractSuite() {
    }

    public static Result run(Driver driver, long seed) {
        Result result = new Result();
        if (driver == null || driver.runtime() == null || driver.player() == null) {
            result.record("eventbridge.driver", false, "driver, runtime or player is null");
            return result;
        }
        movement(result, driver, seed);
        dimensionChange(result, driver, seed);
        portalExit(result, driver, seed);
        dragonDeath(result, driver, seed);
        structureReach(result, driver, seed);
        return result;
    }

    private static void movement(Result result, Driver driver, long seed) {
        String name = "eventbridge.movement";
        SpeedrunLogger.info("Verification " + name + " starting");
        PracticeRuntime runtime = driver.runtime();
        boolean passed = false;
        String detail = null;
        try {
            PracticeSession session = runtime.startPractice(PracticeType.OVERWORLD,
                    settings("overworld.goal", "manual", "timer.start", "player_move"),
                    driver.player(), seed);
            driver.poll();
            boolean armed = !runtime.timer().isRunning();
            driver.movePlayer(2.0, 0.0);
            driver.poll();
            passed = armed && runtime.timer().isRunning()
                    && session.state() == PracticeState.RUNNING;
            if (!passed) {
                detail = "armed=" + armed + ",running=" + runtime.timer().isRunning()
                        + ",state=" + session.state();
            }
        } catch (Exception failure) {
            detail = message(failure);
        } finally {
            stopQuietly(runtime);
        }
        result.record(name, passed, detail == null ? "timer started on real move" : detail);
        SpeedrunLogger.info("Verification " + name + " " + (passed ? "passed" : "failed")
                + (detail == null ? "" : ": " + detail));
    }

    private static void dimensionChange(Result result, Driver driver, long seed) {
        String name = "eventbridge.dim_change";
        SpeedrunLogger.info("Verification " + name + " starting");
        PracticeRuntime runtime = driver.runtime();
        boolean passed = false;
        String detail = null;
        try {
            PracticeSession session = runtime.startPractice(PracticeType.OVERWORLD,
                    settings("overworld.goal", "manual", "timer.start", "dimension_entry"),
                    driver.player(), seed);
            driver.poll();
            boolean armed = !runtime.timer().isRunning();
            driver.enterNether();
            driver.poll();
            passed = armed && runtime.timer().isRunning()
                    && session.state() == PracticeState.RUNNING;
            if (!passed) {
                detail = "armed=" + armed + ",running=" + runtime.timer().isRunning()
                        + ",state=" + session.state();
            }
        } catch (Exception failure) {
            detail = message(failure);
        } finally {
            stopQuietly(runtime);
        }
        result.record(name, passed, detail == null ? "timer started on real dim change" : detail);
        SpeedrunLogger.info("Verification " + name + " " + (passed ? "passed" : "failed")
                + (detail == null ? "" : ": " + detail));
    }

    private static void portalExit(Result result, Driver driver, long seed) {
        String name = "eventbridge.portal_exit";
        SpeedrunLogger.info("Verification " + name + " starting");
        PracticeRuntime runtime = driver.runtime();
        boolean passed = false;
        String detail = null;
        try {
            PracticeSession session = runtime.startPractice(PracticeType.OVERWORLD,
                    settings("overworld.goal", "manual", "timer.start", "portal_exit"),
                    driver.player(), seed);
            driver.poll();
            boolean armed = !runtime.timer().isRunning();
            // A real overworld-&gt;nether trip: the poller fires
            // DimensionChanged (must NOT start a portal_exit timer) and then
            // PortalExit (must start it).
            driver.enterNether();
            driver.poll();
            passed = armed && runtime.timer().isRunning()
                    && session.state() == PracticeState.RUNNING;
            if (!passed) {
                detail = "armed=" + armed + ",running=" + runtime.timer().isRunning()
                        + ",state=" + session.state();
            }
        } catch (Exception failure) {
            detail = message(failure);
        } finally {
            stopQuietly(runtime);
        }
        result.record(name, passed, detail == null ? "timer started on real portal exit" : detail);
        SpeedrunLogger.info("Verification " + name + " " + (passed ? "passed" : "failed")
                + (detail == null ? "" : ": " + detail));
    }

    private static void dragonDeath(Result result, Driver driver, long seed) {
        String name = "eventbridge.dragon_death";
        SpeedrunLogger.info("Verification " + name + " starting");
        PracticeRuntime runtime = driver.runtime();
        boolean passed = false;
        String detail = null;
        try {
            PracticeSession session = runtime.startPractice(PracticeType.END,
                    settings("timer.stop", "dragon_death"), driver.player(), seed);
            pollTen(driver);
            boolean calm = session.state() == PracticeState.RUNNING && !driver.hasLivingDragon();
            driver.spawnDragon();
            pollTen(driver);
            boolean latched = session.state() == PracticeState.RUNNING && driver.hasLivingDragon();
            driver.killDragon();
            pollTen(driver);
            passed = calm && latched && session.state() == PracticeState.COMPLETED;
            if (!passed) {
                detail = "calm=" + calm + ",latched=" + latched + ",state=" + session.state();
            }
        } catch (Exception failure) {
            detail = message(failure);
        } finally {
            stopQuietly(runtime);
        }
        result.record(name, passed, detail == null ? "real dragon death completed end" : detail);
        SpeedrunLogger.info("Verification " + name + " " + (passed ? "passed" : "failed")
                + (detail == null ? "" : ": " + detail));
    }

    private static void structureReach(Result result, Driver driver, long seed) {
        String name = "eventbridge.structure_reach";
        SpeedrunLogger.info("Verification " + name + " starting");
        PracticeRuntime runtime = driver.runtime();
        boolean passed = false;
        String detail = null;
        try {
            PracticeSession session = runtime.startPractice(PracticeType.OVERWORLD,
                    settings("overworld.goal", "manual",
                            "timer.start", "scenario_load",
                            "timer.stop", "structure_reached"),
                    driver.player(), seed);
            driver.poll();
            boolean found = driver.enterPolledStructure();
            pollTen(driver);
            passed = found && session.state() == PracticeState.COMPLETED;
            if (!passed) {
                detail = "found=" + found + ",state=" + session.state();
            }
        } catch (Exception failure) {
            detail = message(failure);
        } finally {
            stopQuietly(runtime);
        }
        result.record(name, passed, detail == null ? "real structure entry completed run" : detail);
        SpeedrunLogger.info("Verification " + name + " " + (passed ? "passed" : "failed")
                + (detail == null ? "" : ": " + detail));
    }

    private static void pollTen(Driver driver) throws Exception {
        // Dragon/structure scans run every 10th poller tick; ten passes
        // always include one scan window.
        for (int i = 0; i < 10; i++) {
            driver.poll();
        }
    }

    private static void stopQuietly(PracticeRuntime runtime) {
        try {
            if (runtime.hasActive()) {
                runtime.stop();
            }
        } catch (Exception ignored) {
            // The check verdict is already recorded; shutdown prunes leftovers.
        }
    }

    private static PracticeSettings settings(String... values) {
        PracticeSettings settings = new PracticeSettings();
        for (int i = 0; i + 1 < values.length; i += 2) {
            settings.set(values[i], values[i + 1]);
        }
        return settings;
    }

    private static String message(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + ": "
                + (message == null ? "no message" : message.replace('\n', ' ').replace(',', ';'));
    }

    public static final class Result {
        private int matches;
        private int total;
        private final List<String> evidence = new ArrayList<String>();

        private void record(String name, boolean passed, String detail) {
            total++;
            if (passed) {
                matches++;
                evidence.add(name);
            } else {
                evidence.add(name + "=FAILED{" + detail + "}");
            }
        }

        public int matches() {
            return matches;
        }

        public int total() {
            return total;
        }

        public List<String> evidence() {
            return new ArrayList<String>(evidence);
        }
    }
}
