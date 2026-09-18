package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.SpeedrunPractice;
import com.gregor0410.speedrunpractice.common.adapter.TimerAdapter;

/**
 * SpeedrunIGT bridge (legacy parity): the mod resets IGT's timer on every
 * practice start. IGT owns start/stop itself, so only reset forwards; the
 * shared monotonic timer always runs alongside.
 */
final class LiveTimer implements TimerAdapter {
    @Override
    public boolean isAvailable() {
        return SpeedrunPractice.speedrunIGTInterface != null;
    }

    @Override
    public void resetTimer() {
        if (SpeedrunPractice.speedrunIGTInterface != null) {
            SpeedrunPractice.speedrunIGTInterface.resetTimer();
        }
    }

    @Override
    public void startTimer() {
        // IGT starts on its own triggers; the legacy path only ever resets.
    }

    @Override
    public void stopTimer() {
        // IGT stops on its own triggers; the legacy path only ever resets.
    }

    @Override
    public void pauseTimer() {
        // No IGT pause hook; the shared timer pauses independently.
    }
}
