package com.gregor0410.speedrunpractice.common.checkpoint;

import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.timer.PracticeTimer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Save/restore/clear of {@link PracticeCheckpoint}s, one slot per practice. */
public interface CheckpointManager {
    void save(PracticeContext context) throws PracticeException;

    void restore(PracticeContext context) throws PracticeException;

    void clear(PracticeId practice);

    boolean has(PracticeId practice);

    /**
     * Saves a checkpoint including scenario state (plan section 39). The
     * default ignores the scenario; the engine always calls this overload.
     */
    default void save(PracticeContext context, PracticeScenario scenario) throws PracticeException {
        save(context);
    }

    /** Restores a checkpoint including scenario state; see {@link #save(PracticeContext, PracticeScenario)}. */
    default void restore(PracticeContext context, PracticeScenario scenario) throws PracticeException {
        restore(context);
    }

    /** In-memory implementation; disk persistence can wrap/replace it later. */
    final class InMemoryCheckpointManager implements CheckpointManager {
        private final MinecraftAdapter adapter;
        private final PracticeTimer timer;
        private final Map<PracticeId, PracticeCheckpoint> checkpoints = new HashMap<PracticeId, PracticeCheckpoint>();

        public InMemoryCheckpointManager(MinecraftAdapter adapter, PracticeTimer timer) {
            if (adapter == null || timer == null) {
                throw new IllegalArgumentException("adapter and timer must not be null");
            }
            this.adapter = adapter;
            this.timer = timer;
        }

        @Override
        public synchronized void save(PracticeContext context) throws PracticeException {
            save(context, null);
        }

        /**
         * Saves a checkpoint, including {@code scenario} state when given
         * (plan section 39). The engine always passes its active scenario.
         */
        @Override
        public synchronized void save(PracticeContext context, PracticeScenario scenario)
                throws PracticeException {
            requireReady(context);
            PracticeCheckpoint.PlayerSnapshot player = capturePlayer(context);
            PracticePosition position = player.position();
            PracticeCheckpoint.ScenarioSnapshot scenarioSnapshot =
                    scenario == null ? null : scenario.captureState(context);
            PracticeCheckpoint checkpoint = new PracticeCheckpoint(
                    context.session().practiceId(), context.seed(),
                    context.world().dimension(), position, player, scenarioSnapshot,
                    new PracticeCheckpoint.TimerSnapshot(timer.elapsedMs(), timer.isRunning()));
            checkpoints.put(context.session().practiceId(), checkpoint);
        }

        @Override
        public synchronized void restore(PracticeContext context) throws PracticeException {
            restore(context, null);
        }

        /**
         * Restores a checkpoint. When the checkpoint seed/dimension differs
         * from the live world, the world is recreated first so the player is
         * never teleported into a wrongly generated world (plan section 40).
         */
        @Override
        public synchronized void restore(PracticeContext context, PracticeScenario scenario)
                throws PracticeException {
            requireReady(context);
            PracticeCheckpoint checkpoint = checkpoints.get(context.session().practiceId());
            if (checkpoint == null) {
                throw new PracticeException.CheckpointException(
                        "No checkpoint for " + context.session().practiceId(),
                        "No checkpoint saved yet. Save one first.");
            }
            if (checkpoint.seed() != context.world().seed()
                    || checkpoint.dimension() != context.world().dimension()) {
                WorldAdapter.PracticeWorldOptions options = WorldAdapter.PracticeWorldOptions
                        .builder(checkpoint.dimension())
                        .generateStructures(context.settings().getBoolean("world.generateStructures", true))
                        .bonusChest(context.settings().getBoolean("world.bonusChest", false))
                        .build();
                adapter.worlds().resetPracticeWorld(context.world(), checkpoint.seed(), options);
            }
            context.setSeed(checkpoint.seed());
            restorePlayer(context, checkpoint.player());
            if (scenario != null) {
                scenario.restoreState(context, checkpoint.scenario());
            }
            timer.setElapsedMs(checkpoint.timer().elapsedMs());
            if (checkpoint.timer().running()) {
                timer.start();
            } else {
                timer.stop();
            }
        }

        @Override
        public synchronized void clear(PracticeId practice) {
            checkpoints.remove(practice);
        }

        @Override
        public synchronized boolean has(PracticeId practice) {
            return checkpoints.containsKey(practice);
        }

        private PracticeCheckpoint.PlayerSnapshot capturePlayer(PracticeContext context)
                throws PracticeException {
            try {
                return adapter.players().capturePlayerState(context.player());
            } catch (PracticeException.AdapterException unsupported) {
                // Degraded path for adapters without snapshot support: capture
                // what the granular getters expose. Saturation, XP, effects
                // and selected slot are unavailable there and restore as
                // defaults; full fidelity needs capturePlayerState.
                PracticePosition position = adapter.players().getPosition(context.player());
                double health = adapter.players().getHealth(context.player());
                int food = adapter.players().getFood(context.player());
                Loadout inventory = null;
                try {
                    inventory = adapter.inventories().captureLoadout(context.player(), "checkpoint");
                } catch (PracticeException ignored) {
                    // Inventory capture is best-effort; position/vitals still save.
                }
                return new PracticeCheckpoint.PlayerSnapshot(position, health, food, 0.0f, 0, 0,
                        inventory, Collections.<String>emptyList(), 0);
            }
        }

        private void restorePlayer(PracticeContext context, PracticeCheckpoint.PlayerSnapshot snapshot)
                throws PracticeException {
            try {
                adapter.players().restorePlayerState(context.player(), snapshot);
                return;
            } catch (PracticeException.AdapterException unsupported) {
                // Degraded path mirroring capturePlayer above.
            }
            adapter.players().teleport(context.player(), snapshot.position());
            adapter.players().setHealth(context.player(), snapshot.health());
            adapter.players().setFood(context.player(), snapshot.food());
            adapter.players().clearEffects(context.player());
            if (snapshot.inventory() != null) {
                adapter.players().applyLoadout(context.player(), snapshot.inventory());
            }
        }

        private void requireReady(PracticeContext context) throws PracticeException.CheckpointException {
            if (context == null || context.session() == null || context.world() == null || context.player() == null) {
                throw new PracticeException.CheckpointException("Checkpoint needs an active session, world and player",
                        "Cannot use checkpoints: no practice is running.");
            }
        }
    }
}
