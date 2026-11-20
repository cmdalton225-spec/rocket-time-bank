package com.cmdalton.rocket.tablet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    private val t0 = 1_000_000L

    private fun shown(s: WidgetState) = WidgetPlan.view(s, 0, s.sessionSeconds * 1000L, 0).let { it.clock to it.label }

    @Test fun midFlightChargesBothBalances() {
        val s = WidgetState(bankSeconds = 1200, sessionSeconds = 300, running = true,
            sessionStatus = "running", savedAtMillis = t0).withElapsedSession(t0 + 120_000)
        assertEquals(180, s.sessionSeconds)
        assertEquals(1080, s.bankSeconds)
        assertTrue(s.running)
        assertEquals("03:00" to "flying", shown(s))
    }

    @Test fun expiredFlightLandsAndShowsTheRemainingBank() {
        // Kit's example: a five-minute flight against a twenty-minute bank.
        val s = WidgetState(bankSeconds = 1200, sessionSeconds = 300, running = true,
            sessionStatus = "running", savedAtMillis = t0).withElapsedSession(t0 + 3_600_000)
        assertEquals(0, s.sessionSeconds)
        assertEquals(900, s.bankSeconds)
        assertFalse(s.running)
        assertEquals("idle", s.sessionStatus)
        assertEquals("15:00" to "in the bank", shown(s))
    }

    @Test fun flightEndsWhenTheBankRunsOutFirst() {
        // Kit's example: a 600 s session against a 300 s bank, after five minutes.
        val s = WidgetState(bankSeconds = 300, sessionSeconds = 600, running = true,
            sessionStatus = "running", savedAtMillis = t0).withElapsedSession(t0 + 300_000)
        assertEquals(0, s.bankSeconds)
        assertFalse(s.running)
        assertEquals("idle", s.sessionStatus)
        assertEquals("00:00" to "in the bank", shown(s))
    }

    @Test fun notRunningIsUnchanged() {
        val s = WidgetState(bankSeconds = 600, sessionSeconds = 0, savedAtMillis = t0)
        assertEquals(s, s.withElapsedSession(t0 + 999_000))
    }
}
