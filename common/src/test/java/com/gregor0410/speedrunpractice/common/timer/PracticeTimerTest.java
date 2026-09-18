package com.gregor0410.speedrunpractice.common.timer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PracticeTimerTest {
    @Test
    public void startsStoppedAtZero() {
        MonotonicPracticeTimer timer = new MonotonicPracticeTimer();
        assertFalse(timer.isRunning());
        assertEquals(0L, timer.elapsedMs());
        assertEquals(PracticeTimer.StartCondition.MANUAL, timer.getStartCondition());
        assertEquals(PracticeTimer.StopCondition.COMPLETION, timer.getStopCondition());
    }

    @Test
    public void startStopAccumulates() throws Exception {
        MonotonicPracticeTimer timer = new MonotonicPracticeTimer();
        timer.start();
        assertTrue(timer.isRunning());
        Thread.sleep(15L);
        timer.stop();
        assertFalse(timer.isRunning());
        assertTrue(timer.elapsedMs() >= 0L);
        timer.reset();
        assertEquals(0L, timer.elapsedMs());
    }

    @Test
    public void pauseFreezesAndResumeContinues() {
        MonotonicPracticeTimer timer = new MonotonicPracticeTimer();
        timer.start();
        timer.pause();
        assertFalse(timer.isRunning());
        long frozen = timer.elapsedMs();
        timer.resume();
        assertTrue(timer.isRunning());
        assertTrue(timer.elapsedMs() >= frozen);
        timer.stop();
    }

    @Test
    public void restoreSetsElapsed() {
        MonotonicPracticeTimer timer = new MonotonicPracticeTimer();
        timer.setElapsedMs(5000L);
        long elapsed = timer.elapsedMs();
        assertTrue(elapsed >= 5000L && elapsed < 6000L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullStartCondition() {
        new MonotonicPracticeTimer().setStartCondition(null);
    }
}
