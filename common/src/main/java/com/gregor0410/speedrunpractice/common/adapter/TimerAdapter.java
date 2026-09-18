package com.gregor0410.speedrunpractice.common.adapter;

/**
 * Optional SpeedRunIGT integration (plan section 24). The internal
 * {@code PracticeTimer} always works; this adapter only mirrors state to IGT
 * when the mod is present. Never required.
 */
public interface TimerAdapter {
    /** False when SpeedRunIGT is absent; all other calls must then be no-ops. */
    boolean isAvailable();

    void resetTimer();

    void startTimer();

    void stopTimer();

    void pauseTimer();
}
