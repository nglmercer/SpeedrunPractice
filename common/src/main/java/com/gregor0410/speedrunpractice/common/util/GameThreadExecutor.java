package com.gregor0410.speedrunpractice.common.util;

/**
 * Thread-handoff contract (plan section 69). Seed math, parsing, stats and
 * disk I/O may run on worker threads; every Minecraft state mutation must
 * return to the game/server thread through this executor, implemented once
 * per version (typically {@code server.execute(...)} / client equivalents).
 */
public interface GameThreadExecutor {
    /** Runs the task on the server/game thread. */
    void server(Runnable task);

    /** Runs the task on the client/render thread. */
    void client(Runnable task);

    /** Runs tasks immediately on the calling thread; for unit tests only. */
    final class DirectGameThreadExecutor implements GameThreadExecutor {
        @Override
        public void server(Runnable task) {
            require(task).run();
        }

        @Override
        public void client(Runnable task) {
            require(task).run();
        }

        private static Runnable require(Runnable task) {
            if (task == null) {
                throw new IllegalArgumentException("task must not be null");
            }
            return task;
        }
    }
}
