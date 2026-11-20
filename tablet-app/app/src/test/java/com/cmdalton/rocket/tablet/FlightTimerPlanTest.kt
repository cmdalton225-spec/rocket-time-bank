package com.cmdalton.rocket.tablet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightTimerPlanTest {
    private val now = 1_000_000L

    @Test fun noTimerYetSchedulesOne() =
        assertTrue(FlightTimerPlan.needsSchedule(0, now, 300))

    @Test fun sameFlightEveryReRenderKeepsTheOneTimer() {
        val end = now + 300_000
        // one second later the flight reports 299 s left: same end, no second timer
        assertFalse(FlightTimerPlan.needsSchedule(end, now + 1_000, 299))
        // a re-render that's a couple of seconds off still matches
        assertFalse(FlightTimerPlan.needsSchedule(end, now + 1_000, 297))
    }

    @Test fun dadAddsTimeMovesTheTimer() =
        assertTrue(FlightTimerPlan.needsSchedule(now + 300_000, now, 900))

    @Test fun nothingLeftSchedulesNothing() =
        assertFalse(FlightTimerPlan.needsSchedule(0, now, 0))

    @Test fun flightEndsWhenEitherBalanceRunsOut() {
        assertEquals(120, FlightTimerPlan.secondsLeft(bankSeconds = 120, sessionSeconds = 600))
        assertEquals(600, FlightTimerPlan.secondsLeft(bankSeconds = 3000, sessionSeconds = 600))
        assertEquals(0, FlightTimerPlan.secondsLeft(bankSeconds = -5, sessionSeconds = 600))
    }
}
