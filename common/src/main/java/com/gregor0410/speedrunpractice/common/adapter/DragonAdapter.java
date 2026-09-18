package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;

/** Dragon-fight helpers for End / One-Cycle practice. */
public interface DragonAdapter {
    /** Clears fight NBT/bar state so a fresh fight starts (legacy EndPractice behaviour). */
    void resetFight(PracticeWorld world) throws PracticeException;

    /** Forces the perch phase; requires {@link Capability#DRAGON_FORCE_PERCH}. */
    void forcePerch(PracticeWorld world) throws PracticeException;

    boolean hasLivingDragon(PracticeWorld world) throws PracticeException;
}
