package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;

/** Nether-portal creation/linking for linked practice worlds. */
public interface PortalAdapter {
    void createNetherPortal(PracticeWorld world, PracticePosition position) throws PracticeException;

    /** Builds both sides of a linked overworld <-> nether portal pair. */
    void linkPortals(PracticeWorld overworld, PracticeWorld nether, PracticePosition overworldPos)
            throws PracticeException;
}
