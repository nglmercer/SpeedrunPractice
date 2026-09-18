package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
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
}
