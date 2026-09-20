package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Verification-only repeated world lifecycle contract (plan phase 8).
 * Version probes provide the leak check because only a version adapter can
 * inspect its live tracking table without putting Minecraft types in shared
 * code.
 */
public final class WorldLifecycleContractSuite {
    private static final int CYCLES = 50;

    private WorldLifecycleContractSuite() {
    }

    public static Result run(MinecraftAdapter adapter, LeakCheck leakCheck) {
        Result result = new Result();
        if (adapter == null || leakCheck == null) {
            result.record(false, "adapter or leak check is null");
            return result;
        }
        WorldAdapter.PracticeWorldOptions options = WorldAdapter.PracticeWorldOptions
                .builder(PracticeDimension.END)
                .generateStructures(false)
                .bonusChest(false)
                .build();
        SpeedrunLogger.info("Verification world.lifecycle starting cycles=" + CYCLES);
        PracticeWorld active = null;
        try {
            for (int cycle = 0; cycle < CYCLES; cycle++) {
                long seedA = 700000L + cycle;
                long seedB = 800000L + cycle;

                active = adapter.worlds().createPracticeWorld(seedA, options);
                requireSeed(active, seedA, cycle, "create A");
                adapter.worlds().deletePracticeWorld(active);
                active = null;
                requireClean(leakCheck, cycle, "delete A");

                active = adapter.worlds().createPracticeWorld(seedA, options);
                requireSeed(active, seedA, cycle, "recreate A");
                adapter.worlds().resetPracticeWorld(active, seedA, options);
                requireSeed(active, seedA, cycle, "reset A");
                adapter.worlds().resetPracticeWorld(active, seedB, options);
                requireSeed(active, seedB, cycle, "reset B");
                adapter.worlds().deletePracticeWorld(active);
                active = null;
                requireClean(leakCheck, cycle, "delete B");
            }
            result.record(true, "cycles=" + CYCLES + ", no tracked practice worlds after each delete");
        } catch (Exception failure) {
            result.record(false, message(failure));
        } finally {
            if (active != null) {
                try {
                    adapter.worlds().deletePracticeWorld(active);
                } catch (Exception failure) {
                    SpeedrunLogger.warn("Lifecycle cleanup failed: " + failure.getMessage());
                }
            }
        }
        SpeedrunLogger.info("Verification world.lifecycle "
                + (result.matches() == 1 ? "passed" : "failed")
                + " (" + result.detail() + ")");
        return result;
    }

    private static void requireSeed(PracticeWorld world, long expected, int cycle, String phase)
            throws PracticeException {
        if (world == null || world.seed() != expected) {
            throw new PracticeException("lifecycle cycle " + cycle + " " + phase
                    + " seed mismatch", "Expected seed " + expected + " during " + phase + ".");
        }
    }

    private static void requireClean(LeakCheck leakCheck, int cycle, String phase)
            throws PracticeException {
        if (!leakCheck.isClean()) {
            throw new PracticeException("lifecycle cycle " + cycle + " leaked after " + phase,
                    "A deleted practice world is still tracked after " + phase + ".");
        }
    }

    private static String message(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + ": "
                + (message == null ? "no message" : message.replace('\n', ' ').replace(',', ';'));
    }

    public interface LeakCheck {
        boolean isClean();
    }

    public static final class Result {
        private int matches;
        private int total;
        private String detail = "not run";

        private void record(boolean passed, String detail) {
            total = 1;
            matches = passed ? 1 : 0;
            this.detail = detail;
        }

        public int matches() {
            return matches;
        }

        public int total() {
            return total;
        }

        public String detail() {
            return detail;
        }

        public List<String> evidence() {
            List<String> evidence = new ArrayList<String>();
            evidence.add(matches == 1 ? "world.lifecycle." + CYCLES + "cycles"
                    : "world.lifecycle=FAILED{" + detail + "}");
            return evidence;
        }
    }
}
