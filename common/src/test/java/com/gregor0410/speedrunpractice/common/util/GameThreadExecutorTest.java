package com.gregor0410.speedrunpractice.common.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Thread-handoff contract: tasks run, nulls rejected. */
public class GameThreadExecutorTest {
    @Test
    public void directExecutorRunsImmediately() {
        GameThreadExecutor executor = new GameThreadExecutor.DirectGameThreadExecutor();
        final AtomicBoolean server = new AtomicBoolean();
        final AtomicBoolean client = new AtomicBoolean();
        executor.server(new Runnable() {
            @Override
            public void run() {
                server.set(true);
            }
        });
        executor.client(new Runnable() {
            @Override
            public void run() {
                client.set(true);
            }
        });
        assertTrue(server.get());
        assertTrue(client.get());
    }

    @Test
    public void nullTasksRejected() {
        GameThreadExecutor executor = new GameThreadExecutor.DirectGameThreadExecutor();
        try {
            executor.server(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            executor.client(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
