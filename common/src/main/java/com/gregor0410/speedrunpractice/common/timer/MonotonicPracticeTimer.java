package com.gregor0410.speedrunpractice.common.timer;

/** {@code nanoTime}-based monotonic timer. */
public final class MonotonicPracticeTimer implements PracticeTimer {
    private StartCondition startCondition = StartCondition.MANUAL;
    private StopCondition stopCondition = StopCondition.COMPLETION;
    private long accumulatedNs;
    private long startedNs = -1L;
    private boolean paused;

    @Override
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        startedNs = System.nanoTime();
        paused = false;
    }

    @Override
    public synchronized void stop() {
        if (isRunning()) {
            accumulatedNs += System.nanoTime() - startedNs;
            startedNs = -1L;
        }
        paused = false;
    }

    @Override
    public synchronized void pause() {
        if (isRunning()) {
            accumulatedNs += System.nanoTime() - startedNs;
            startedNs = -1L;
            paused = true;
        }
    }

    @Override
    public synchronized void resume() {
        if (paused && !isRunning()) {
            startedNs = System.nanoTime();
            paused = false;
        }
    }

    @Override
    public synchronized void reset() {
        accumulatedNs = 0L;
        startedNs = -1L;
        paused = false;
    }

    @Override
    public synchronized long elapsedMs() {
        long total = accumulatedNs;
        if (isRunning()) {
            total += System.nanoTime() - startedNs;
        }
        return total / 1000000L;
    }

    @Override
    public synchronized boolean isRunning() {
        return startedNs >= 0L;
    }

    @Override
    public synchronized void setElapsedMs(long elapsedMs) {
        accumulatedNs = Math.max(0L, elapsedMs) * 1000000L;
        if (isRunning()) {
            startedNs = System.nanoTime();
        }
    }

    @Override
    public synchronized StartCondition getStartCondition() {
        return startCondition;
    }

    @Override
    public synchronized void setStartCondition(StartCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("start condition must not be null");
        }
        this.startCondition = condition;
    }

    @Override
    public synchronized StopCondition getStopCondition() {
        return stopCondition;
    }

    @Override
    public synchronized void setStopCondition(StopCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("stop condition must not be null");
        }
        this.stopCondition = condition;
    }
}
