package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.adapter.TimerAdapter;

/**
 * External-timer bridge on 1.21.1. No SpeedrunIGT-style timer mod is on
 * the classpath for this version, so the bridge reports unavailable and
 * every timer call is a documented no-op; the shared monotonic timer
 * always runs alongside and is the real practice timer here.
 */
final class LiveTimer121 implements TimerAdapter {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void resetTimer() {
        // No external timer to reset; the shared timer resets independently.
    }

    @Override
    public void startTimer() {
        // No external timer to start; the shared timer starts independently.
    }

    @Override
    public void stopTimer() {
        // No external timer to stop; the shared timer stops independently.
    }

    @Override
    public void pauseTimer() {
        // No external timer to pause; the shared timer pauses independently.
    }
}
