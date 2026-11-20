package com.cmdalton.rocket.tablet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPlanTest {
    @Test fun clockIsMinutesSecondsUnderAnHourAndHoursMinutesSecondsFromAnHour() {
        assertEquals("00:00", WidgetPlan.clock(0))
        assertEquals("00:59", WidgetPlan.clock(59))
        assertEquals("12:05", WidgetPlan.clock(725))
        assertEquals("59:59", WidgetPlan.clock(3599))
        assertEquals("1:00:00", WidgetPlan.clock(3600))
        assertEquals("1:23:45", WidgetPlan.clock(5025))
        assertEquals("00:00", WidgetPlan.clock(-4))
    }

    /** What an Android countdown Chronometer based at [base] shows at [now] (it truncates toward zero). */
    private fun chronometerShows(base: Long, now: Long) = WidgetPlan.clock((base - now) / 1000)

    /** What the cockpit's session clock shows with [remainingMs] true time left (live-ui.js fmt() rounds up). */
    private fun cockpitShows(remainingMs: Long) = WidgetPlan.clock((remainingMs + 999) / 1000)

    @Test fun tickingCountdownMatchesTheCockpitEverySecond() {
        val start = 1_000_000L
        val remaining = 125_400L
        val base = WidgetPlan.freshBase(start, remaining)
        for (t in 0..125_000L step 100) {
            assertEquals("at +${t}ms", cockpitShows(remaining - t), chronometerShows(base, start + t))
        }
    }

    @Test fun syncWithinASecondKeepsTheCountdownSteady() {
        val stored = WidgetPlan.freshBase(1_000_000, 300_000)
        // a push one second later reporting 299 s (whole seconds; true 298.9 s): same countdown, no jump
        val fresh = WidgetPlan.freshBase(1_001_000, WidgetPlan.remainingFromPush(299))
        assertEquals(stored, WidgetPlan.base(stored, fresh))
        // drift of a second or more is corrected (Dad added time: a real change)
        val dad = WidgetPlan.freshBase(1_001_000, 899_000)
        assertEquals(dad, WidgetPlan.base(stored, dad))
    }

    @Test fun runningShowsTheTickingCountdownAndPause() {
        val v = WidgetPlan.view(WidgetState(3000, 1200, true, "running"), 50_000, 1_199_900, 0)
        assertTrue(v.ticking)
        assertEquals(WidgetView.Button.PAUSE, v.button)
        assertEquals("flying", v.label)
        assertEquals(50_000 + 1_199_900 + WidgetPlan.ROUND_UP_MS, v.chronoBase)
    }

    @Test fun pausedIsFrozenWithPlay() {
        val v = WidgetPlan.view(WidgetState(2958, 1158, false, "paused"), 0, 0, 0)
        assertFalse(v.ticking)
        assertEquals("19:18", v.clock)
        assertEquals("paused", v.label)
        assertEquals(WidgetView.Button.PLAY, v.button)
    }

    @Test fun readyToPlayOffersPlayLikeTheCockpitsStartButton() {
        val v = WidgetPlan.view(WidgetState(5000, 3900, false, "approved"), 0, 0, 0)
        assertEquals("1:05:00", v.clock)
        assertEquals(WidgetView.Button.PLAY, v.button)
    }

    @Test fun idleShowsTheBankInTheSameFormatWithNoButton() {
        val v = WidgetPlan.view(WidgetState(3054, 0, false, "idle"), 0, 0, 0)
        assertEquals("50:54", v.clock)
        assertEquals("in the bank", v.label)
        assertEquals(WidgetView.Button.NONE, v.button)
        assertEquals("1:25:00", WidgetPlan.view(WidgetState(5100, 0, false, "idle"), 0, 0, 0).clock)
    }

    @Test fun pausedWithAnEmptyBankHasNothingToPlay() {
        assertEquals(WidgetView.Button.NONE, WidgetPlan.view(WidgetState(0, 600, false, "paused"), 0, 0, 0).button)
    }

    @Test fun extrapolatedFlightLandsOnThePreciseCountdown() {
        val saved = WidgetState(3000, 10, true, "running", savedAtMillis = 1_000_000, remainingMsAtSave = 9_900)
        assertTrue(saved.withElapsedSession(1_009_800).running)
        val landed = saved.withElapsedSession(1_009_950)
        assertFalse(landed.running)
        assertEquals("idle", landed.sessionStatus)
        assertEquals(2990, landed.bankSeconds)
    }
}
