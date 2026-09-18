package com.gregor0410.speedrunpractice.common.engine;

import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeState;
import com.gregor0410.speedrunpractice.common.checkpoint.CheckpointManager;
import com.gregor0410.speedrunpractice.common.events.PracticeEvent;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutManager;
import com.gregor0410.speedrunpractice.common.seeds.SeedRequest;
import com.gregor0410.speedrunpractice.common.seeds.SeedSource;
import com.gregor0410.speedrunpractice.common.stats.PracticeStatistics;
import com.gregor0410.speedrunpractice.common.timer.PracticeTimer;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

/**
 * Runs the one scenario lifecycle for solo practice (plan sections 6 and 11).
 * Single active session; owns timer, statistics, checkpoint and seed-source
 * wiring so scenarios stay focused on world/player setup.
 *
 * <p>Timer handling (plan section 20): the timer is always reset on start,
 * but only started immediately for {@code scenario_load}; every other start
 * condition waits for its event via {@link #fireEvent(PracticeEvent)}.
 * Stop conditions other than {@code scenario_complete} complete the attempt
 * from their event. Scenarios additionally poll completion in
 * {@link PracticeScenario#tick(PracticeContext)} so attempts still finish on
 * versions whose adapters do not emit events yet.
 *
 * <p>Failure handling (plan sections 89 and 90): a start that throws after
 * mutating game state cleans up its partial world, records a failed attempt
 * and throws a {@link PracticeException} (raw runtime exceptions are
 * wrapped, never leaked). A failed reset leaves the session in
 * {@code FAILED}, recoverable via reset, stop or a fresh start.
 */
public final class ScenarioEngine {
    /** Context attribute holding the shared {@link LoadoutManager}. */
    public static final String LOADOUTS_ATTRIBUTE = "loadouts";

    private final MinecraftAdapter adapter;
    private final CheckpointManager checkpoints;
    private final PracticeTimer timer;
    private final PracticeStatistics stats;
    private final LoadoutManager loadouts;
    private PracticeScenario currentScenario;
    private PracticeContext currentContext;

    public ScenarioEngine(MinecraftAdapter adapter, CheckpointManager checkpoints,
                          PracticeTimer timer, PracticeStatistics stats, LoadoutManager loadouts) {
        if (adapter == null || checkpoints == null || timer == null || stats == null || loadouts == null) {
            throw new IllegalArgumentException("engine collaborators must not be null");
        }
        this.adapter = adapter;
        this.checkpoints = checkpoints;
        this.timer = timer;
        this.stats = stats;
        this.loadouts = loadouts;
    }

    public boolean hasActive() {
        return currentScenario != null && currentContext != null;
    }

    public PracticeScenario currentScenario() {
        return currentScenario;
    }

    public PracticeContext currentContext() {
        return currentContext;
    }

    public PracticeTimer timer() {
        return timer;
    }

    public PracticeStatistics statistics() {
        return stats;
    }

    public PracticeSession startScenario(PracticeScenario scenario, PracticeSettings settings,
                                         PracticePlayer player, long seed) throws PracticeException {
        if (scenario == null || player == null) {
            throw new IllegalArgumentException("scenario and player must not be null");
        }
        if (hasActive()) {
            stopCurrent();
        }
        PracticeSettings effective = settings == null ? new PracticeSettings() : settings;
        PracticeSession session = new PracticeSession(scenario.id(), seed);
        PracticeContext context = new PracticeContext(session, adapter, null, player, effective, seed);
        context.setAttribute(LOADOUTS_ATTRIBUTE, loadouts);
        session.transitionTo(PracticeState.PREPARING);
        try {
            scenario.prepare(context);
            session.transitionTo(PracticeState.RUNNING);
            session.countAttempt();
            scenario.start(context);
        } catch (PracticeException failure) {
            failStart(session, context, scenario, failure);
            throw failure;
        } catch (RuntimeException failure) {
            failStart(session, context, scenario, failure);
            throw new PracticeException("Scenario " + scenario.id() + " failed to start: " + failure,
                    "Could not start " + scenario.id() + ". Try another seed or practice.", failure);
        }
        applyTimerStart(effective);
        currentScenario = scenario;
        currentContext = context;
        SpeedrunLogger.info("Started scenario " + scenario.id() + " on seed " + seed);
        return session;
    }

    public PracticeScenario.TickResult tickCurrent() throws PracticeException {
        ensureActive();
        if (currentContext.session().state() != PracticeState.RUNNING) {
            return PracticeScenario.TickResult.continueTick();
        }
        PracticeScenario.TickResult result = currentScenario.tick(currentContext);
        if (result.isFinished()) {
            completeAttempt();
        }
        return result;
    }

    /**
     * Dispatches one version event to the timer conditions and the active
     * scenario. Unlike {@link #tickCurrent()}, this is lenient when idle or
     * not running — game events fire constantly and must never throw just
     * because no practice is active — and returns
     * {@link PracticeScenario.TickResult#continueTick()} in that case.
     */
    public PracticeScenario.TickResult fireEvent(PracticeEvent event) throws PracticeException {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (!hasActive() || currentContext.session().state() != PracticeState.RUNNING) {
            return PracticeScenario.TickResult.continueTick();
        }
        maybeStartTimer(event);
        PracticeScenario.TickResult result = currentScenario.onEvent(currentContext, event);
        if (result.isFinished() && currentContext.session().state() == PracticeState.RUNNING) {
            completeAttempt();
        }
        maybeStopTimer(event);
        return currentContext.session().state() == PracticeState.COMPLETED
                ? PracticeScenario.TickResult.finished()
                : result;
    }

    public void resetCurrent(PracticeScenario.ResetMode mode, SeedSource seedSource) throws PracticeException {
        ensureActive();
        if (mode == null) {
            throw new IllegalArgumentException("reset mode must not be null");
        }
        if (mode == PracticeScenario.ResetMode.CHECKPOINT) {
            long abandonedMs = timer.elapsedMs();
            checkpoints.restore(currentContext, currentScenario);
            recordAttempt(currentScenario, currentContext, false, mode.name(), abandonedMs);
            SpeedrunLogger.info("Restored checkpoint for " + currentScenario.id());
            return;
        }
        if (mode == PracticeScenario.ResetMode.FULL_RESET) {
            PracticeScenario scenario = currentScenario;
            PracticeSettings settings = currentContext.settings();
            PracticePlayer player = currentContext.player();
            stopCurrent(); // records the abandoned attempt
            startScenario(scenario, settings, player, nextSeed(seedSource, mode));
            return;
        }
        long seed = currentContext.seed();
        if (mode == PracticeScenario.ResetMode.NEW_SEED) {
            seed = nextSeed(seedSource, mode);
        } else if (mode == PracticeScenario.ResetMode.PREVIOUS_SEED) {
            seed = previousSeed(seedSource);
        }
        recordAttempt(false, mode.name());
        currentContext.session().transitionTo(PracticeState.PREPARING);
        currentContext.setSeed(seed);
        try {
            currentScenario.reset(currentContext, mode);
        } catch (PracticeException failure) {
            failReset(failure);
            throw failure;
        } catch (RuntimeException failure) {
            failReset(failure);
            throw new PracticeException("Scenario " + currentScenario.id() + " failed to reset: " + failure,
                    "Could not reset " + currentScenario.id() + ". Stop and start it again.", failure);
        }
        currentContext.session().transitionTo(PracticeState.RUNNING);
        currentContext.session().countAttempt();
        applyTimerStart(currentContext.settings());
        SpeedrunLogger.info("Reset scenario " + currentScenario.id() + " (" + mode + ") on seed " + seed);
    }

    public void stopCurrent() throws PracticeException {
        if (!hasActive()) {
            return;
        }
        PracticeState state = currentContext.session().state();
        if (state == PracticeState.RUNNING || state == PracticeState.PAUSED) {
            stopTimer();
            recordAttempt(false, "STOP");
            currentContext.session().transitionTo(PracticeState.STOPPING);
            state = PracticeState.STOPPING;
        }
        try {
            currentScenario.stop(currentContext);
        } finally {
            try {
                if (state == PracticeState.COMPLETED) {
                    currentContext.session().transitionTo(PracticeState.IDLE);
                } else {
                    currentContext.session().transitionTo(PracticeState.STOPPED);
                }
            } catch (IllegalStateException bad) {
                SpeedrunLogger.warn("Session ended in an unexpected state: " + bad.getMessage());
            }
            currentScenario = null;
            currentContext = null;
        }
    }

    public void saveCheckpointCurrent() throws PracticeException {
        ensureActive();
        checkpoints.save(currentContext, currentScenario);
    }

    public boolean hasCheckpointCurrent() {
        return hasActive() && checkpoints.has(currentScenario.id());
    }

    public void clearCheckpointCurrent() {
        if (hasActive()) {
            checkpoints.clear(currentScenario.id());
        }
    }

    private void ensureActive() {
        if (!hasActive()) {
            throw new IllegalStateException("No active practice scenario");
        }
    }

    /**
     * Deletes the partial world of a failed start, marks the session failed
     * and records the attempt. The engine is left idle so a fresh start or a
     * different practice works immediately.
     */
    private void failStart(PracticeSession session, PracticeContext context, PracticeScenario scenario,
                           Throwable failure) {
        SpeedrunLogger.warn("Scenario " + scenario.id() + " failed to start: " + failure);
        cleanupPartialStart(context, scenario);
        stopTimerQuietly();
        try {
            session.transitionTo(PracticeState.FAILED);
        } catch (IllegalStateException bad) {
            SpeedrunLogger.warn("Could not mark failed session: " + bad.getMessage());
        }
        try {
            recordAttempt(scenario, context, false, "FAILED", 0L);
        } catch (RuntimeException bad) {
            SpeedrunLogger.warn("Could not record failed start: " + bad.getMessage());
        }
    }

    private void cleanupPartialStart(PracticeContext context, PracticeScenario scenario) {
        try {
            scenario.stop(context);
        } catch (PracticeException failure) {
            SpeedrunLogger.warn("Error while cleaning up failed start: " + failure.getMessage());
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Error while cleaning up failed start: " + failure.getMessage());
        }
        if (context.world() != null) {
            try {
                adapter.worlds().deletePracticeWorld(context.world());
            } catch (PracticeException failure) {
                SpeedrunLogger.warn("Could not delete partial practice world: " + failure.getUserMessage());
            } catch (RuntimeException failure) {
                SpeedrunLogger.warn("Could not delete partial practice world: " + failure.getMessage());
            } finally {
                context.setWorld(null);
            }
        }
    }

    /**
     * Marks a failed reset and deletes the half-rebuilt world. The session
     * stays active in {@code FAILED} so callers can inspect it; reset, stop
     * or a fresh start recovers.
     */
    private void failReset(Throwable failure) {
        SpeedrunLogger.warn("Scenario " + currentScenario.id() + " failed to reset: " + failure);
        cleanupPartialStart(currentContext, currentScenario);
        stopTimerQuietly();
        try {
            currentContext.session().transitionTo(PracticeState.FAILED);
        } catch (IllegalStateException bad) {
            SpeedrunLogger.warn("Could not mark failed reset: " + bad.getMessage());
        }
    }

    private void completeAttempt() {
        stopTimer();
        recordAttempt(true, null);
        currentContext.session().transitionTo(PracticeState.COMPLETED);
        SpeedrunLogger.info("Completed scenario " + currentScenario.id()
                + " in " + timer.elapsedMs() + "ms");
    }

    /**
     * Resets the timer and starts it only for {@code scenario_load} (plan
     * section 20). Unknown condition names warn and fall back to the
     * load/manual defaults instead of breaking the run.
     */
    private void applyTimerStart(PracticeSettings settings) {
        PracticeTimer.StartCondition start = PracticeTimer.parseStart(settings.get("timer.start"));
        if (start == null) {
            warnUnknown(settings.get("timer.start"), "timer.start", "scenario_load");
            start = PracticeTimer.StartCondition.SCENARIO_LOAD;
        }
        PracticeTimer.StopCondition stop = PracticeTimer.parseStop(settings.get("timer.stop"));
        if (stop == null) {
            warnUnknown(settings.get("timer.stop"), "timer.stop", "scenario_complete");
            stop = PracticeTimer.StopCondition.COMPLETION;
        }
        timer.setStartCondition(start);
        timer.setStopCondition(stop);
        resetTimer();
        if (start == PracticeTimer.StartCondition.SCENARIO_LOAD) {
            startTimer();
        }
    }

    private static void warnUnknown(String raw, String key, String fallback) {
        if (raw != null && !raw.trim().isEmpty()) {
            SpeedrunLogger.warn("Unknown " + key + " \"" + raw + "\"; using " + fallback);
        }
    }

    private void maybeStartTimer(PracticeEvent event) {
        if (timer.isRunning()) {
            return;
        }
        PracticeTimer.StartCondition start = timer.getStartCondition();
        if (start == PracticeTimer.StartCondition.FIRST_MOVEMENT && event instanceof PracticeEvent.PlayerMovedEvent) {
            startTimer();
        } else if (start == PracticeTimer.StartCondition.DIMENSION_ENTRY
                && event instanceof PracticeEvent.DimensionChangedEvent) {
            startTimer();
        } else if (start == PracticeTimer.StartCondition.PORTAL_EXIT
                && event instanceof PracticeEvent.PortalExitEvent) {
            startTimer();
        } else if (start == PracticeTimer.StartCondition.MANUAL
                && event instanceof PracticeEvent.ManualTimerStartEvent) {
            startTimer();
        }
    }

    private void maybeStopTimer(PracticeEvent event) {
        if (currentContext.session().state() != PracticeState.RUNNING || !timer.isRunning()) {
            return;
        }
        PracticeTimer.StopCondition stop = timer.getStopCondition();
        if (stop == PracticeTimer.StopCondition.DIMENSION_ENTRY
                && event instanceof PracticeEvent.DimensionChangedEvent) {
            completeAttempt();
        } else if (stop == PracticeTimer.StopCondition.STRUCTURE_REACHED
                && event instanceof PracticeEvent.StructureEnteredEvent) {
            completeAttempt();
        } else if (stop == PracticeTimer.StopCondition.DRAGON_DEATH
                && event instanceof PracticeEvent.DragonKilledEvent
                && isOwnWorld((PracticeEvent.DragonKilledEvent) event)) {
            completeAttempt();
        } else if (stop == PracticeTimer.StopCondition.MANUAL
                && event instanceof PracticeEvent.ManualTimerStopEvent) {
            completeAttempt();
        }
    }

    private boolean isOwnWorld(PracticeEvent.DragonKilledEvent event) {
        return currentContext.world() != null
                && currentContext.world().handleId().equals(event.worldHandle());
    }

    private void resetTimer() {
        timer.reset();
        if (adapter.timer().isAvailable()) {
            adapter.timer().resetTimer();
        }
    }

    private void startTimer() {
        timer.start();
        if (adapter.timer().isAvailable()) {
            adapter.timer().startTimer();
        }
    }

    private void stopTimer() {
        timer.stop();
        if (adapter.timer().isAvailable()) {
            adapter.timer().stopTimer();
        }
    }

    private void stopTimerQuietly() {
        try {
            stopTimer();
        } catch (RuntimeException failure) {
            SpeedrunLogger.warn("Could not stop timer during failure cleanup: " + failure.getMessage());
        }
    }

    private void recordAttempt(boolean completed, String resetReason) {
        recordAttempt(currentScenario, currentContext, completed, resetReason, timer.elapsedMs());
    }

    private void recordAttempt(PracticeScenario scenario, PracticeContext context, boolean completed,
                               String resetReason, long elapsedMs) {
        stats.recordAttempt(new PracticeStatistics.AttemptRecord(
                scenario.id(), adapter.version(), context.seed(),
                elapsedMs, completed, System.currentTimeMillis(),
                resetReason, context.settings().get("preset")));
    }

    private long nextSeed(SeedSource seedSource, PracticeScenario.ResetMode mode) throws PracticeException {
        if (seedSource == null) {
            throw new PracticeException.SeedSearchException("No seed source for reset " + mode,
                    "Cannot pick a new seed: no seed source is configured.");
        }
        java.util.OptionalLong next = seedSource.nextSeed(SeedRequest.any());
        if (!next.isPresent()) {
            throw new PracticeException.SeedSearchException("Seed source exhausted on reset " + mode,
                    "No matching seed available. Adjust the search or seed list.");
        }
        return next.getAsLong();
    }

    private long previousSeed(SeedSource seedSource) throws PracticeException {
        if (seedSource == null) {
            throw new PracticeException.SeedSearchException("No seed source for previous-seed reset",
                    "Cannot go to the previous seed: no seed source is configured.");
        }
        java.util.OptionalLong previous = seedSource.previousSeed();
        if (!previous.isPresent()) {
            throw new PracticeException.SeedSearchException("No previous seed available",
                    "No previous seed available yet.");
        }
        return previous.getAsLong();
    }
}
