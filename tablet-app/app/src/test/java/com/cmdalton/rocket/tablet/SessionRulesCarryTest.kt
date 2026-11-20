package com.cmdalton.rocket.tablet

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRulesCarryTest {
    private fun doc(at: Long, bank: Long = 3000, session: Long = 1200) = BankDoc(bank, session, true, "running", at)

    @Test fun settledCarryCountsOnlyForItsOwnCharge() {
        val c = Carry(atMs = 1_000, seconds = 0.7)
        assertEquals(10, SessionRules.elapsedSeconds(doc(1_000), 10_400, c))          // 9.4 s + 0.7 s
        assertEquals(9, SessionRules.elapsedSeconds(doc(2_000), 11_400, c))           // another charge since: none
    }

    @Test fun aPendingChargeThatLandedLeavesWhatTheCockpitWouldHaveSettled() {
        val c = Carry(atMs = null, seconds = 0.0, pending = Carry.Pending(fromMs = 0, carryIn = 0.5, spent = 5, bankSeconds = 2995, sessionSeconds = 1195))
        // the charge landed at 5_800 ms: 5.8 s flown + 0.5 carried in - 5 written = 1.3 s still owed
        assertEquals(1.3, c.owedFor(doc(5_800, 2995, 1195)), 1e-9)
        assertEquals(0.0, c.owedFor(doc(5_800, 2990, 1190)), 0.0)                    // a different write landed
        assertEquals(0.0, c.owedFor(doc(0, 2995, 1195)), 0.0)                        // it never landed
    }

    @Test fun pauseNeverSpendsMoreThanTheBalances() {
        val c = Carry(atMs = 1_000, seconds = 5_000.0)
        val w = SessionRules.decide(SessionRules.PAUSE, doc(1_000, bank = 3000, session = 1200), 2_000, c) as SessionWrite.Update
        assertEquals(1800L, w.bankSeconds)
        assertEquals(0L, w.sessionSeconds)
        assertEquals("idle", w.sessionStatus)
    }
}
