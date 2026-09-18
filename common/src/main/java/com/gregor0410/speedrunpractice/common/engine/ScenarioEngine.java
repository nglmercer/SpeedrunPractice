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
        } catch (PracticeException failure) {
            session.transitionTo(PracticeState.STOPPED);
            throw failure;
        }
        session.transitionTo(PracticeState.RUNNING);
        session.countAttempt();
        scenario.start(context);
        startTimer();
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
            stopTimer();
            recordAttempt(true, null);
            currentContext.session().transitionTo(PracticeState.COMPLETED);
            SpeedrunLogger.info("Completed scenario " + currentScenario.id()
                    + " in " + timer.elapsedMs() + "ms");
        }
        return result;
    }

    public void resetCurrent(PracticeScenario.ResetMode mode, SeedSource seedSource) throws PracticeException {
        ensureActive();
        if (mode == null) {
            throw new IllegalArgumentException("reset mode must not be null");
        }
        if (mode == PracticeScenario.ResetMode.CHECKPOINT) {
            recordAttempt(false, mode.name());
            checkpoints.restore(currentContext);
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
        currentScenario.reset(currentContext, mode);
        currentContext.session().transitionTo(PracticeState.RUNNING);
        currentContext.session().countAttempt();
        startTimer();
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
        }
        try {
            currentScenario.stop(currentContext);
        } finally {
            if (state == PracticeState.COMPLETED) {
                currentContext.session().transitionTo(PracticeState.IDLE);
            } else {
                currentContext.session().transitionTo(PracticeState.STOPPED);
            }
            currentScenario = null;
            currentContext = null;
        }
    }

    public void saveCheckpointCurrent() throws PracticeException {
        ensureActive();
        checkpoints.save(currentContext);
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

    private void startTimer() {
        timer.reset();
        timer.start();
        if (adapter.timer().isAvailable()) {
            adapter.timer().resetTimer();
            adapter.timer().startTimer();
        }
    }

    private void stopTimer() {
        timer.stop();
        if (adapter.timer().isAvailable()) {
            adapter.timer().stopTimer();
        }
    }

    private void recordAttempt(boolean completed, String resetReason) {
        stats.recordAttempt(new PracticeStatistics.AttemptRecord(
                currentScenario.id(), adapter.version(), currentContext.seed(),
                timer.elapsedMs(), completed, System.currentTimeMillis(),
                resetReason, currentContext.settings().get("preset")));
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
