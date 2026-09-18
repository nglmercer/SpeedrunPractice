package com.gregor0410.speedrunpractice.common.timer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Lenient timer-condition parsing for run settings (plan section 20). */
public class TimerConditionParseTest {
    @Test
    public void parsesStartConditions() {
        assertEquals(PracticeTimer.StartCondition.SCENARIO_LOAD, PracticeTimer.parseStart("scenario_load"));
        assertEquals(PracticeTimer.StartCondition.FIRST_MOVEMENT, PracticeTimer.parseStart("player_move"));
        assertEquals(PracticeTimer.StartCondition.DIMENSION_ENTRY, PracticeTimer.parseStart("dimension_entry"));
        assertEquals(PracticeTimer.StartCondition.PORTAL_EXIT, PracticeTimer.parseStart("portal_exit"));
        assertEquals(PracticeTimer.StartCondition.MANUAL, PracticeTimer.parseStart("manual"));
        assertEquals(PracticeTimer.StartCondition.MANUAL, PracticeTimer.parseStart(" MANUAL "));
    }

    @Test
    public void parsesStopConditions() {
        assertEquals(PracticeTimer.StopCondition.COMPLETION, PracticeTimer.parseStop("scenario_complete"));
        assertEquals(PracticeTimer.StopCondition.DIMENSION_ENTRY, PracticeTimer.parseStop("dimension_entry"));
        assertEquals(PracticeTimer.StopCondition.STRUCTURE_REACHED, PracticeTimer.parseStop("structure_reached"));
        assertEquals(PracticeTimer.StopCondition.DRAGON_DEATH, PracticeTimer.parseStop("dragon_death"));
        assertEquals(PracticeTimer.StopCondition.MANUAL, PracticeTimer.parseStop("manual"));
    }

    @Test
    public void unknownOrBlankIsNull() {
        assertNull(PracticeTimer.parseStart(null));
        assertNull(PracticeTimer.parseStart(""));
        assertNull(PracticeTimer.parseStart("  "));
        assertNull(PracticeTimer.parseStart("warp_drive"));
        assertNull(PracticeTimer.parseStop(null));
        assertNull(PracticeTimer.parseStop(""));
        assertNull(PracticeTimer.parseStop("warp_drive"));
    }
}
