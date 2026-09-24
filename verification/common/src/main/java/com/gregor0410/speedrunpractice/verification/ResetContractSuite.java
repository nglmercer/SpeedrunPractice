package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.engine.ScenarioEngine;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

/**
 * Verification-only reset contract for the real practice runtime (plan
 * phase 8). Drives one Overworld session through the full reset ladder —
 * start on seed A, {@code SAME_SEED} (still A), {@code NEW_SEED} (B, never
 * A), {@code PREVIOUS_SEED} (back to A), {@code restartOnSeed(C)} — and
 * checks after every step that the scenario is still valid and running on
 * the expected seed, that the world was actually rebuilt (new handle), and
 * that the old world was deleted (tracked count stays at baseline plus the
 * per-practice footprint the start established: one world on fakes, one
 * linked triple on live adapters). Stopping must return the count to
 * baseline: no leaked worlds.
 *
 * <p>This class deliberately depends on {@link PracticeRuntime}, not on a
 * fake Minecraft adapter: version-local probes supply a real live player
 * handle plus a leak check, exactly like {@link ScenarioContractSuite}.
 */
public final class ResetContractSuite {
    /** Start seed: SAME_SEED and PREVIOUS_SEED must return here. */
    public static final long SEED_A = 910001L;
    /** Second list seed: NEW_SEED must land here (never A). */
    public static final long SEED_B = 910002L;
    /** Explicit restart seed for {@code restartOnSeed}. */
    public static final long SEED_C = 910003L;
    private static final String IMPORT_NAME = "verification-resets";

    private ResetContractSuite() {
    }

    public static Result run(PracticeRuntime runtime, PracticePlayer player, LeakCheck leakCheck) {
        Result result = new Result();
        if (runtime == null || player == null || leakCheck == null) {
            result.record("reset.runtime", false, "runtime, player or leak check is null");
            return result;
        }
        SpeedrunLogger.info("Verification reset suite starting");
        List<String> backup = new ArrayList<String>();
        try {
            for (Long seed : runtime.seedStore().readImport(IMPORT_NAME)) {
                backup.add(Long.toString(seed));
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Could not back up seed import " + IMPORT_NAME + ": " + failure.getMessage());
        }
        try {
            runtime.seedStore().writeImport(IMPORT_NAME,
                    Arrays.<String>asList(Long.toString(SEED_A), Long.toString(SEED_B)));
        } catch (Exception failure) {
            result.record("reset.seeds", false, message(failure));
            return result;
        }
        try {
            if (runtime.hasActive()) {
                runtime.stop();
            }
        } catch (Exception failure) {
            result.record("reset.start", false, "could not stop stale session: " + message(failure));
            restoreImport(runtime, backup);
            return result;
        }
        int baseline;
        try {
            baseline = leakCheck.trackedCount();
        } catch (Exception failure) {
            result.record("reset.start", false, "leak check failed: " + message(failure));
            restoreImport(runtime, backup);
            return result;
        }
        String[] handle = new String[1];
        int[] footprint = new int[]{-1};
        try {
            if (!startPhase(result, runtime, player, leakCheck, baseline, footprint, handle)) {
                return result;
            }
            if (!resetPhase(result, runtime, PracticeScenario.ResetMode.SAME_SEED, SEED_A,
                    leakCheck, baseline, footprint, handle)) {
                return result;
            }
            if (!resetPhase(result, runtime, PracticeScenario.ResetMode.NEW_SEED, SEED_B,
                    leakCheck, baseline, footprint, handle)) {
                return result;
            }
            if (!resetPhase(result, runtime, PracticeScenario.ResetMode.PREVIOUS_SEED, SEED_A,
                    leakCheck, baseline, footprint, handle)) {
                return result;
            }
            if (!restartPhase(result, runtime, leakCheck, baseline, footprint, handle)) {
                return result;
            }
        } finally {
            try {
                if (runtime.hasActive()) {
                    runtime.stop();
                }
                int tracked = leakCheck.trackedCount();
                if (tracked == baseline) {
                    result.record("reset.cleanup", true, "tracked=" + tracked);
                    SpeedrunLogger.info("Verification reset.cleanup passed");
                } else {
                    result.record("reset.cleanup", false,
                            "leaked practice worlds: tracked=" + tracked + " baseline=" + baseline);
                    SpeedrunLogger.info("Verification reset.cleanup failed");
                }
            } catch (Exception failure) {
                result.record("reset.cleanup", false, message(failure));
            }
            restoreImport(runtime, backup);
        }
        return result;
    }

    private static boolean startPhase(Result result, PracticeRuntime runtime, PracticePlayer player,
                                      LeakCheck leakCheck, int baseline, int[] footprint,
                                      String[] handle) {
        SpeedrunLogger.info("Verification reset.start starting");
        try {
            PracticeSettings settings = new PracticeSettings();
            settings.set("seed.source", "imported");
            settings.set("seed.list", IMPORT_NAME);
            PracticeSession session = runtime.startPractice(PracticeType.OVERWORLD, settings, player);
            return check(result, "reset.start", runtime, session, SEED_A, null, leakCheck,
                    baseline, footprint, handle);
        } catch (Exception failure) {
            result.record("reset.start", false, message(failure));
            SpeedrunLogger.info("Verification reset.start failed: " + message(failure));
            return false;
        }
    }

    private static boolean resetPhase(Result result, PracticeRuntime runtime, PracticeScenario.ResetMode mode,
                                      long expected, LeakCheck leakCheck, int baseline,
                                      int[] footprint, String[] handle) {
        String name = "reset." + mode.name().toLowerCase();
        SpeedrunLogger.info("Verification " + name + " starting");
        try {
            runtime.reset(mode);
            if (mode == PracticeScenario.ResetMode.NEW_SEED) {
                OptionalLong current = runtime.currentSeed();
                if (current.isPresent() && current.getAsLong() == SEED_A) {
                    result.record(name, false, "new seed equals start seed " + SEED_A);
                    SpeedrunLogger.info("Verification " + name + " failed: new seed equals " + SEED_A);
                    return false;
                }
            }
            PracticeSession session = runtime.engine().currentContext().session();
            return check(result, name, runtime, session, expected, handle[0], leakCheck,
                    baseline, footprint, handle);
        } catch (Exception failure) {
            result.record(name, false, message(failure));
            SpeedrunLogger.info("Verification " + name + " failed: " + message(failure));
            return false;
        }
    }

    private static boolean restartPhase(Result result, PracticeRuntime runtime,
                                        LeakCheck leakCheck, int baseline, int[] footprint,
                                        String[] handle) {
        SpeedrunLogger.info("Verification reset.restart_seed starting");
        try {
            runtime.restartOnSeed(SEED_C);
            PracticeSession session = runtime.engine().currentContext().session();
            return check(result, "reset.restart_seed", runtime, session, SEED_C, handle[0],
                    leakCheck, baseline, footprint, handle);
        } catch (Exception failure) {
            result.record("reset.restart_seed", false, message(failure));
            SpeedrunLogger.info("Verification reset.restart_seed failed: " + message(failure));
            return false;
        }
    }

    private static boolean check(Result result, String name, PracticeRuntime runtime, PracticeSession session,
                                 long expected, String previousHandle, LeakCheck leakCheck, int baseline,
                                 int[] footprint, String[] handle) {
        String failure = verify(runtime, session, expected, previousHandle, leakCheck, baseline,
                footprint);
        if (failure == null) {
            handle[0] = runtime.engine().currentContext().world().handleId();
            result.record(name, true, "seed=" + expected);
            SpeedrunLogger.info("Verification " + name + " passed");
            return true;
        }
        result.record(name, false, failure);
        SpeedrunLogger.info("Verification " + name + " failed: " + failure);
        return false;
    }

    /**
     * Null when the phase is fully valid: an active RUNNING Overworld
     * scenario on {@code expected} (session, context and world seeds agree),
     * rebuilt onto a fresh world handle, with exactly one practice worth of
     * worlds above baseline (the old ones were deleted, nothing leaked).
     * The start establishes the per-practice footprint (one world on fakes,
     * one linked triple on live adapters); every later phase must match it.
     */
    private static String verify(PracticeRuntime runtime, PracticeSession session, long expected,
                                 String previousHandle, LeakCheck leakCheck, int baseline,
                                 int[] footprint) {
        if (!runtime.hasActive()) {
            return "no active practice";
        }
        if (session == null) {
            return "session is null";
        }
        if (session.state() != PracticeState.RUNNING) {
            return "state=" + session.state() + ", want RUNNING";
        }
        ScenarioEngine engine = runtime.engine();
        if (engine.currentScenario() == null || engine.currentScenario().type() != PracticeType.OVERWORLD) {
            return "scenario is not overworld";
        }
        if (engine.currentContext() == null || engine.currentContext().world() == null) {
            return "context or world is null";
        }
        PracticeWorld world = engine.currentContext().world();
        OptionalLong current = runtime.currentSeed();
        if (!current.isPresent() || current.getAsLong() != expected) {
            return "currentSeed=" + (current.isPresent() ? current.getAsLong() : "empty")
                    + ", want " + expected;
        }
        if (session.seed() != expected) {
            return "session.seed=" + session.seed() + ", want " + expected;
        }
        if (world.seed() != expected) {
            return "world.seed=" + world.seed() + ", want " + expected;
        }
        if (previousHandle != null && previousHandle.equals(world.handleId())) {
            return "world was not rebuilt (handle " + world.handleId() + " reused)";
        }
        int tracked = leakCheck.trackedCount();
        if (footprint[0] < 0) {
            if (tracked <= baseline) {
                return "tracked=" + tracked + ", want more than baseline=" + baseline
                        + " (no world created?)";
            }
            footprint[0] = tracked - baseline;
            return null;
        }
        if (tracked != baseline + footprint[0]) {
            return "tracked=" + tracked + ", want baseline+" + footprint[0]
                    + "=" + (baseline + footprint[0]) + " (old world leaked?)";
        }
        return null;
    }

    private static void restoreImport(PracticeRuntime runtime, List<String> backup) {
        try {
            runtime.seedStore().writeImport(IMPORT_NAME, backup);
        } catch (Exception failure) {
            SpeedrunLogger.warn("Could not restore seed import " + IMPORT_NAME + ": " + failure.getMessage());
        }
    }

    private static String message(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + ": "
                + (message == null ? "no message" : message.replace('\n', ' ').replace(',', ';'));
    }

    /** Version-supplied world count; the harness implements it with {@code worldCount()}. */
    public interface LeakCheck {
        int trackedCount();
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
