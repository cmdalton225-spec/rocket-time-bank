package com.cmdalton.rocket

import com.cmdalton.rocket.bank.Bank
import com.cmdalton.rocket.bank.HISTORY_LIMIT
import com.cmdalton.rocket.bank.Pin
import com.cmdalton.rocket.bank.RocketState
import com.cmdalton.rocket.bank.StateJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The saved bank (so it survives restarts) and the grown-up PIN. */
class SaveFormatTest {
    private val t0 = 1_800_000_000_000L

    @Test fun everyStateRoundTrips() {
        var s = Bank.addTime(RocketState(), 30, t0)
        assertEquals(s, StateJson.decode(StateJson.encode(s)))
        s = Bank.request(s, 25, t0 + 1)
        assertEquals(s, StateJson.decode(StateJson.encode(s)))
        s = Bank.approve(s, t0 + 2)
        assertEquals(s, StateJson.decode(StateJson.encode(s)))
        s = Bank.markClockSynced(Bank.play(s, t0 + 3))
        assertEquals(s, StateJson.decode(StateJson.encode(s)))
        s = Bank.pause(s, t0 + 60_000, auto = true)
        assertEquals(s, StateJson.decode(StateJson.encode(s)))
        s = Bank.tick(Bank.play(s, t0 + 70_000), t0 + 99_000_000)
        assertEquals(s, StateJson.decode(StateJson.encode(s)))
    }

    @Test fun emptyOrBrokenSaveStartsFresh() {
        assertEquals(RocketState(), StateJson.decode(null))
        assertEquals(RocketState(), StateJson.decode(""))
        assertEquals(RocketState(), StateJson.decode("{not json"))
    }

    @Test fun unknownHistoryLinesAreSkippedNotFatal() {
        val s = StateJson.decode(
            """{"v":1,"balanceSec":600,"history":[{"at":1,"kind":"FUTURE_THING","sec":1},{"at":2,"kind":"ADDED","sec":600}]}""",
        )
        assertEquals(600L, s.balanceSec)
        assertEquals(1, s.history.size)
    }

    @Test fun negativeBalanceIsNeverLoaded() {
        assertEquals(0L, StateJson.decode("""{"balanceSec":-50}""").balanceSec)
    }

    @Test fun historyIsCapped() {
        var s = RocketState()
        repeat(HISTORY_LIMIT + 50) { s = Bank.addTime(s, 5, t0 + it) }
        assertEquals(HISTORY_LIMIT, s.history.size)
        assertEquals(HISTORY_LIMIT, StateJson.decode(StateJson.encode(s)).history.size)
        assertEquals((HISTORY_LIMIT + 50) * 5 * 60L, s.balanceSec)
    }

    @Test fun pinMustBeFourDigits() {
        assertTrue(Pin.isValidFormat("0429"))
        assertFalse(Pin.isValidFormat("123"))
        assertFalse(Pin.isValidFormat("12345"))
        assertFalse(Pin.isValidFormat("12a4"))
    }

    @Test fun pinIsCheckedAgainstASaltedHash() {
        val salt = Pin.newSalt()
        val hash = Pin.hash("2468", salt)
        assertNotEquals("2468", hash)
        assertTrue(Pin.matches("2468", salt, hash))
        assertFalse(Pin.matches("2469", salt, hash))
        assertFalse(Pin.matches("2468", null, null))
        assertNotEquals(hash, Pin.hash("2468", Pin.newSalt()))
    }
}
