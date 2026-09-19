package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.practices.PracticeRuntime;

/**
 * Live-event poller for 1.21.1. Minimal until the players/worlds slices
 * land (plan section 9): with no practicable world there is nothing to
 * poll, so this is a no-op placeholder with the final call shape.
 */
final class EventPoller121 {
    private final LiveAdapter121 adapter;

    EventPoller121(LiveAdapter121 adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        this.adapter = adapter;
    }

    void poll(PracticeRuntime runtime) {
        // No live events until worlds/players land; the scenario poll in
        // Runtime121 still runs every tick.
    }
}
