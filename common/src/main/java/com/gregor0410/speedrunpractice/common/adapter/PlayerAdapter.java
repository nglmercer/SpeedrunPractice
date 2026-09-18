package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.checkpoint.PracticeCheckpoint;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;

/** Player teleport / vitals / effects / loadouts, owned by the version adapter. */
public interface PlayerAdapter {
    void teleport(PracticePlayer player, PracticePosition position) throws PracticeException;

    void setHealth(PracticePlayer player, double health) throws PracticeException;

    void setFood(PracticePlayer player, int food) throws PracticeException;

    void clearEffects(PracticePlayer player) throws PracticeException;

    void applyLoadout(PracticePlayer player, Loadout loadout) throws PracticeException;

    /** Full reset (health/XP/food/effects/velocity/air), mirroring legacy resetPlayer. */
    void resetPlayer(PracticePlayer player) throws PracticeException;

    PracticePosition getPosition(PracticePlayer player) throws PracticeException;

    double getHealth(PracticePlayer player) throws PracticeException;

    int getFood(PracticePlayer player) throws PracticeException;

    /** The world the player is currently standing in. */
    PracticeWorld getWorld(PracticePlayer player) throws PracticeException;

    /**
     * Captures full player state for checkpoints (plan section 38: position,
     * rotation, health, food, saturation, XP, level, inventory, armor,
     * offhand, selected slot, status effects). The default reports
     * unsupported, in which case the checkpoint manager falls back to the
     * granular getters above with degraded fidelity.
     */
    default PracticeCheckpoint.PlayerSnapshot capturePlayerState(PracticePlayer player)
            throws PracticeException {
        throw new PracticeException.AdapterException("Player snapshots are not supported here",
                "Checkpoints cannot capture full player state on this version yet.");
    }

    /**
     * Restores state previously captured by
     * {@link #capturePlayerState(PracticePlayer)}. See that method for the
     * degraded fallback.
     */
    default void restorePlayerState(PracticePlayer player, PracticeCheckpoint.PlayerSnapshot snapshot)
            throws PracticeException {
        throw new PracticeException.AdapterException("Player snapshots are not supported here",
                "Checkpoints cannot restore full player state on this version yet.");
    }
}
