package com.gregor0410.speedrunpractice.common.checkpoint;

import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
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
            requireReady(context);
            PracticePosition position = adapter.players().getPosition(context.player());
            double health = adapter.players().getHealth(context.player());
            int food = adapter.players().getFood(context.player());
            Loadout inventory = null;
            try {
                inventory = adapter.inventories().captureLoadout(context.player(), "checkpoint");
            } catch (PracticeException ignored) {
                // Inventory capture is best-effort; position/vitals still save.
            }
            PracticeCheckpoint.PlayerSnapshot player = new PracticeCheckpoint.PlayerSnapshot(
                    position, health, food, 5.0f, 0, 0, inventory,
                    Collections.<String>emptyList(), 0);
            PracticeCheckpoint checkpoint = new PracticeCheckpoint(
                    context.session().practiceId(), context.seed(),
                    context.world().dimension(), position, player, null,
                    new PracticeCheckpoint.TimerSnapshot(timer.elapsedMs(), timer.isRunning()));
            checkpoints.put(context.session().practiceId(), checkpoint);
        }

        @Override
        public synchronized void restore(PracticeContext context) throws PracticeException {
            requireReady(context);
            PracticeCheckpoint checkpoint = checkpoints.get(context.session().practiceId());
            if (checkpoint == null) {
                throw new PracticeException.CheckpointException(
                        "No checkpoint for " + context.session().practiceId(),
                        "No checkpoint saved yet. Save one first.");
            }
            context.setSeed(checkpoint.seed());
            adapter.players().teleport(context.player(), checkpoint.position());
            adapter.players().setHealth(context.player(), checkpoint.player().health());
            adapter.players().setFood(context.player(), checkpoint.player().food());
            adapter.players().clearEffects(context.player());
            if (checkpoint.player().inventory() != null) {
                adapter.players().applyLoadout(context.player(), checkpoint.player().inventory());
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

        private void requireReady(PracticeContext context) throws PracticeException.CheckpointException {
            if (context == null || context.session() == null || context.world() == null || context.player() == null) {
                throw new PracticeException.CheckpointException("Checkpoint needs an active session, world and player",
                        "Cannot use checkpoints: no practice is running.");
            }
        }
    }
}
