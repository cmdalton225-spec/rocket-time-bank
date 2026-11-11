package com.cmdalton.rocket

import com.cmdalton.rocket.bank.Bank
import com.cmdalton.rocket.bank.Flight
import com.cmdalton.rocket.bank.HistoryKind
import com.cmdalton.rocket.bank.PRESETS_MIN
import com.cmdalton.rocket.bank.RocketState
import com.cmdalton.rocket.bank.STALE_HEARTBEAT_MS
import com.cmdalton.rocket.bank.formatCountdown
import com.cmdalton.rocket.bank.minutesLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bank, request, approval and countdown rules. */
class BankTest {
    private val t0 = 1_800_000_000_000L
    private val MIN = 60_000L

    private fun banked(minutes: Int) = Bank.addTime(RocketState(), minutes, t0)

    // --- bank ---

    @Test fun addTimeBanksMinutesAndLogsIt() {
        val s = Bank.addTime(Bank.addTime(RocketState(), 30, t0), 15, t0 + 1)
        assertEquals(45 * 60L, s.balanceSec)
        assertEquals(45L, s.balanceMin)
        assertEquals(listOf(HistoryKind.ADDED, HistoryKind.ADDED), s.history.map { it.kind })
    }

    @Test fun addTimeIgnoresZeroAndNegative() {
        val s = RocketState()
        assertSame(s, Bank.addTime(s, 0, t0))
        assertSame(s, Bank.addTime(s, -5, t0))
    }

    @Test fun presetsAreMattsChoice() {
        assertEquals(listOf(5, 10, 15, 20, 25, 30), PRESETS_MIN)
    }

    // --- request ---

    @Test fun requestNeedsEnoughInTheBank() {
        val s = banked(20)
        assertSame(s, Bank.request(s, 25, t0))
        val r = Bank.request(s, 20, t0)
        assertEquals(20, r.pendingRequestMin)
        assertEquals(20 * 60L, r.balanceSec) // nothing leaves the bank until approval
        assertEquals(HistoryKind.REQUESTED, r.history.last().kind)
    }

    @Test fun onlyOneRequestAtATimeAndNotDuringAFlight() {
        val pending = Bank.request(banked(60), 10, t0)
        assertSame(pending, Bank.request(pending, 5, t0))
        val approved = Bank.approve(pending, t0)
        assertSame(approved, Bank.request(approved, 5, t0))
    }

    // --- approval ---

    @Test fun approveMovesMinutesFromBankIntoAWaitingFlight() {
        val s = Bank.approve(Bank.request(banked(30), 25, t0), t0 + 1)
        assertEquals(5 * 60L, s.balanceSec)
        assertNull(s.pendingRequestMin)
        val f = s.flight!!
        assertEquals(25 * 60L, f.totalSec)
        assertEquals(25 * MIN, f.remainingMs)
        assertFalse(f.launched)
        assertFalse(f.running)
        assertEquals(HistoryKind.APPROVED, s.history.last().kind)
    }

    @Test fun denyLeavesTheBankAlone() {
        val s = Bank.deny(Bank.request(banked(30), 25, t0), t0 + 1)
        assertEquals(30 * 60L, s.balanceSec)
        assertNull(s.pendingRequestMin)
        assertNull(s.flight)
        assertEquals(HistoryKind.DENIED, s.history.last().kind)
    }

    @Test fun approveWithNothingPendingDoesNothing() {
        val s = banked(30)
        assertSame(s, Bank.approve(s, t0))
        assertSame(s, Bank.deny(s, t0))
        assertFalse(s.canApprove())
    }

    @Test fun approvedFlightDoesNotCountDownUntilPlay() {
        val s = Bank.approve(Bank.request(banked(30), 25, t0), t0)
        assertEquals(25 * MIN, s.flight!!.remainingAt(t0 + 60 * MIN))
    }

    // --- countdown ---

    private fun flying(minutes: Int = 25, at: Long = t0): RocketState =
        Bank.play(Bank.approve(Bank.request(banked(30), minutes, at), at), at)

    @Test fun playStartsTheCountdown() {
        val s = flying()
        val f = s.flight!!
        assertTrue(f.launched)
        assertTrue(f.running)
        assertFalse(f.clockSynced)
        assertEquals(HistoryKind.LAUNCHED, s.history.last().kind)
        assertEquals(25 * MIN, f.remainingAt(t0))
        assertEquals(15 * MIN, f.remainingAt(t0 + 10 * MIN))
        assertEquals(0L, f.remainingAt(t0 + 99 * MIN))
    }

    @Test fun playTwiceDoesNotRestart() {
        val s = flying()
        assertSame(s, Bank.play(s, t0 + MIN))
    }

    @Test fun pauseFreezesTheCountdownAndPlayResumesIt() {
        val paused = Bank.pause(flying(), t0 + 10 * MIN)
        val f = paused.flight!!
        assertFalse(f.running)
        assertFalse(f.autoPaused)
        assertEquals(15 * MIN, f.remainingAt(t0 + 50 * MIN)) // no time burns while paused
        val resumed = Bank.play(paused, t0 + 50 * MIN)
        assertEquals(10 * MIN, resumed.flight!!.remainingAt(t0 + 55 * MIN))
        // Resuming is not a second launch in the history.
        assertEquals(1, resumed.history.count { it.kind == HistoryKind.LAUNCHED })
    }

    @Test fun screenOffPausesAndUnlockResumes() {
        val off = Bank.pause(flying(), t0 + 5 * MIN, auto = true)
        assertTrue(off.flight!!.autoPaused)
        val on = Bank.autoResume(off, t0 + 30 * MIN)
        assertTrue(on.flight!!.running)
        assertEquals(15 * MIN, on.flight!!.remainingAt(t0 + 35 * MIN))
    }

    @Test fun unlockDoesNotResumeAManualPause() {
        val paused = Bank.pause(flying(), t0 + 5 * MIN)
        assertSame(paused, Bank.autoResume(paused, t0 + 6 * MIN))
    }

    @Test fun clockTimerMustBeRestartedAfterEveryPause() {
        val synced = Bank.markClockSynced(flying())
        assertTrue(synced.flight!!.clockSynced)
        val paused = Bank.pause(synced, t0 + MIN, auto = true)
        assertFalse(paused.flight!!.clockSynced)
        assertSame(paused, Bank.markClockSynced(paused)) // only a running flight can be synced
        val resumed = Bank.autoResume(paused, t0 + 2 * MIN)
        assertFalse(resumed.flight!!.clockSynced)
    }

    @Test fun tickLandsTheRocketAtZero() {
        val s = flying()
        val before = Bank.tick(s, t0 + 25 * MIN - 1)
        assertNotNull(before.flight)
        assertNull(before.landedAtMs)
        val landed = Bank.tick(s, t0 + 25 * MIN)
        assertNull(landed.flight)
        assertEquals(t0 + 25 * MIN, landed.landedAtMs)
        assertEquals(HistoryKind.LANDED, landed.history.last().kind)
        assertEquals(5 * 60L, landed.balanceSec) // the used time is gone, the rest stays banked
        assertNull(Bank.dismissLanding(landed).landedAtMs)
    }

    @Test fun tickRecordsAHeartbeatWhileFlying() {
        val s = Bank.tick(flying(), t0 + 7_000)
        assertEquals(t0 + 7_000, s.flight!!.heartbeatMs)
    }

    @Test fun tickDoesNothingWhilePaused() {
        val p = Bank.pause(flying(), t0 + MIN)
        assertSame(p, Bank.tick(p, t0 + 99 * MIN))
    }

    // --- clear (Matt's choice: clears a request, or lands now with a refund) ---

    @Test fun clearCancelsAPendingRequest() {
        val s = Bank.clear(Bank.request(banked(30), 25, t0), t0 + 1)
        assertNull(s.pendingRequestMin)
        assertEquals(30 * 60L, s.balanceSec)
        assertEquals(HistoryKind.CANCELLED, s.history.last().kind)
    }

    @Test fun clearBeforeLaunchRefundsEverything() {
        val s = Bank.clear(Bank.approve(Bank.request(banked(30), 25, t0), t0), t0 + MIN)
        assertNull(s.flight)
        assertEquals(30 * 60L, s.balanceSec)
    }

    @Test fun clearMidFlightRefundsTheUnusedTime() {
        val s = Bank.clear(flying(), t0 + 12 * MIN + 30_000)
        assertNull(s.flight)
        assertNull(s.landedAtMs)
        // 30 - 25 = 5 left, + 12 min 30 s unused
        assertEquals(5 * 60L + 12 * 60L + 30, s.balanceSec)
        assertEquals(HistoryKind.CLEARED, s.history.last().kind)
        assertEquals(12 * 60L + 30, s.history.last().seconds)
    }

    @Test fun clearWhilePausedRefundsTheFrozenTime() {
        val p = Bank.pause(flying(), t0 + 20 * MIN)
        val s = Bank.clear(p, t0 + 90 * MIN)
        assertEquals(5 * 60L + 5 * 60L, s.balanceSec)
    }

    @Test fun clearWithNothingGoingOnDoesNothing() {
        val s = banked(10)
        assertSame(s, Bank.clear(s, t0))
    }

    // --- recovery after the app was not running ---

    @Test fun recoverStopsTimeAtTheLastHeartbeat() {
        val s = Bank.tick(flying(), t0 + 5 * MIN) // heartbeat at 5 min
        val r = Bank.recover(s, t0 + 3 * 60 * MIN) // tablet was off for hours
        val f = r.flight!!
        assertFalse(f.running)
        assertTrue(f.autoPaused)
        assertEquals(20 * MIN, f.remainingMs)
    }

    @Test fun recoverLeavesAFreshFlightAlone() {
        val s = Bank.tick(flying(), t0 + 5 * MIN)
        assertSame(s, Bank.recover(s, t0 + 5 * MIN + STALE_HEARTBEAT_MS))
    }

    @Test fun recoverLandsAFlightThatHadAlreadyRunOut() {
        val s = flying().let { it.copy(flight = it.flight!!.copy(heartbeatMs = t0 + 25 * MIN)) }
        val r = Bank.recover(s, t0 + 200 * MIN)
        assertNull(r.flight)
        assertEquals(t0 + 25 * MIN, r.landedAtMs)
    }

    // --- the whole loop from the spec ---

    @Test fun fullLoopEarnRequestApprovePlayPauseLand() {
        var s = RocketState()
        s = Bank.addTime(s, 30, t0)                       // Matt adds earned time
        s = Bank.request(s, 25, t0 + MIN)                 // Asher asks for 25
        s = Bank.approve(s, t0 + 2 * MIN)                 // Matt approves
        s = Bank.play(s, t0 + 3 * MIN)                    // Asher presses play
        s = Bank.markClockSynced(s)                       // Clock timer accepted
        s = Bank.pause(s, t0 + 13 * MIN, auto = true)     // screen off after 10 min
        s = Bank.autoResume(s, t0 + 40 * MIN)             // back on after a break
        assertEquals(15 * MIN, s.flight!!.remainingAt(t0 + 40 * MIN))
        s = Bank.tick(s, t0 + 55 * MIN)
        assertEquals(t0 + 55 * MIN, s.landedAtMs)
        assertEquals(5L, s.balanceMin)
        assertEquals(
            listOf(HistoryKind.ADDED, HistoryKind.REQUESTED, HistoryKind.APPROVED, HistoryKind.LAUNCHED, HistoryKind.LANDED),
            s.history.map { it.kind },
        )
    }

    // --- display helpers ---

    @Test fun countdownFormat() {
        assertEquals("25:00", formatCountdown(25 * MIN))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("0:00", formatCountdown(0))
        assertEquals("9:59", formatCountdown(10 * MIN - 1000))
        assertEquals("1:05:00", formatCountdown(65 * MIN))
    }

    @Test fun minutesLeftRoundsUp() {
        assertEquals(25L, minutesLeft(25 * MIN))
        assertEquals(1L, minutesLeft(1))
        assertEquals(0L, minutesLeft(0))
        assertEquals(13L, minutesLeft(12 * MIN + 1))
    }

    @Test fun flightRemainingNeverNegative() {
        val f = Flight(totalSec = 60, remainingMs = 60_000, launched = true, runningSinceMs = t0)
        assertEquals(0L, f.remainingAt(t0 + 10 * MIN))
        assertEquals(60_000L, f.remainingAt(t0 - 5_000)) // clock went backwards: no time added
    }
}
