package com.gregor0410.speedrunpractice.common.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Runtime state machine transitions (plan section 90). */
public class PracticeSessionTransitionTest {
    private static PracticeSession running() {
        PracticeSession session = new PracticeSession(PracticeId.of("end"), 1L);
        session.transitionTo(PracticeState.PREPARING);
        session.transitionTo(PracticeState.RUNNING);
        return session;
    }

    @Test
    public void runningStopsThroughStopping() {
        PracticeSession session = running();
        session.transitionTo(PracticeState.STOPPING);
        session.transitionTo(PracticeState.STOPPED);
        assertEquals(PracticeState.STOPPED, session.state());
    }

    @Test
    public void runningFailsAndRecovers() {
        PracticeSession session = running();
        session.transitionTo(PracticeState.FAILED);
        session.transitionTo(PracticeState.PREPARING);
        session.transitionTo(PracticeState.RUNNING);
        assertEquals(PracticeState.RUNNING, session.state());
    }

    @Test
    public void preparingFailsDirectly() {
        PracticeSession session = new PracticeSession(PracticeId.of("end"), 1L);
        session.transitionTo(PracticeState.PREPARING);
        session.transitionTo(PracticeState.FAILED);
        session.transitionTo(PracticeState.STOPPED);
        assertEquals(PracticeState.STOPPED, session.state());
    }

    @Test
    public void completedResetsExplicitly() {
        PracticeSession session = running();
        session.transitionTo(PracticeState.COMPLETED);
        session.transitionTo(PracticeState.PREPARING);
        session.transitionTo(PracticeState.RUNNING);
        assertEquals(PracticeState.RUNNING, session.state());
    }

    @Test
    public void failedStopsCleanly() {
        PracticeSession session = running();
        session.transitionTo(PracticeState.FAILED);
        session.transitionTo(PracticeState.IDLE);
        assertEquals(PracticeState.IDLE, session.state());
    }

    @Test
    public void idleCannotComplete() {
        PracticeSession session = new PracticeSession(PracticeId.of("end"), 1L);
        try {
            session.transitionTo(PracticeState.COMPLETED);
            fail("expected IllegalStateException for IDLE -> COMPLETED");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void stoppedCannotRunDirectly() {
        PracticeSession session = running();
        session.transitionTo(PracticeState.STOPPED);
        try {
            session.transitionTo(PracticeState.RUNNING);
            fail("expected IllegalStateException for STOPPED -> RUNNING");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void nullTransitionRejected() {
        try {
            running().transitionTo(null);
            fail("expected IllegalArgumentException for null");
        } catch (IllegalArgumentException expected) {
        }
    }
}
