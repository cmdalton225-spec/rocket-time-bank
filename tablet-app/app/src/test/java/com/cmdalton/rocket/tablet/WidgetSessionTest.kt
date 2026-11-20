package com.cmdalton.rocket.tablet

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Widget Pause / Play through the real REST client against a rules-enforcing fake Firestore. */
class WidgetSessionTest {
    private val fs = FakeFirestore(serverSkewMs = 5_000)   // the tablet clock is 5 s fast, as on Matt's
    private val creds = ChildCredentials("api-key", "p", "kid-uid", "refresh-abc")
    private fun session() = FirestoreSession(creds, firestoreBase = "${fs.base}/v1", tokenUrl = "${fs.base}/token")

    private fun flying(bank: Long, session: Long, sinceChargeMs: Long) {
        fs.doc = mutableMapOf("bankSeconds" to bank, "sessionSeconds" to session, "running" to true,
            "sessionStatus" to "running", "lastChargedAt" to fs.serverNow() - sinceChargeMs, "updatedAt" to fs.serverNow() - sinceChargeMs,
            "earnedMinutesTotal" to 900L)
    }

    @After fun stop() = fs.close()

    @Test fun pauseChargesTheFlownTimeOnTheServersClockAndStopsTheFlight() {
        flying(bank = 3000, session = 1200, sinceChargeMs = 42_300)
        val o = session().perform(SessionRules.PAUSE)
        assertTrue(o.changed)
        assertEquals(0, fs.refusedCommits)                       // firestore.rules accepted it
        assertEquals(2958L, fs.doc["bankSeconds"])                // 42 s flown (server clock), not 47 (tablet clock)
        assertEquals(1158L, fs.doc["sessionSeconds"])
        assertEquals(false, fs.doc["running"])
        assertEquals("paused", fs.doc["sessionStatus"])
        assertEquals(BankDoc(2958, 1158, false, "paused", fs.doc["lastChargedAt"] as Long), o.doc)
    }

    @Test fun playResumesExactlyWhereThePauseLeftIt() {
        flying(bank = 3000, session = 1200, sinceChargeMs = 10_000)
        session().perform(SessionRules.PAUSE)
        val o = session().perform(SessionRules.START)
        assertTrue(o.changed)
        assertEquals(0, fs.refusedCommits)
        assertEquals(true, fs.doc["running"])
        assertEquals("running", fs.doc["sessionStatus"])
        assertEquals(2990L, fs.doc["bankSeconds"])                // Play spends nothing
        assertEquals(1190L, fs.doc["sessionSeconds"])
        assertEquals(1190L, o.doc.sessionSeconds)
    }

    @Test fun pauseWhenTheTimeIsUsedUpLandsTheFlight() {
        flying(bank = 3000, session = 30, sinceChargeMs = 45_000)
        session().perform(SessionRules.PAUSE)
        assertEquals(0, fs.refusedCommits)
        assertEquals(2970L, fs.doc["bankSeconds"])
        assertEquals(0L, fs.doc["sessionSeconds"])
        assertEquals("idle", fs.doc["sessionStatus"])
    }

    @Test fun playWithoutAnApprovedSessionIsRefusedAndWritesNothing() {
        fs.doc = mutableMapOf("bankSeconds" to 600L, "sessionSeconds" to 0L, "running" to false, "sessionStatus" to "idle",
            "lastChargedAt" to fs.serverNow(), "updatedAt" to fs.serverNow())
        val o = session().perform(SessionRules.START)
        assertFalse(o.changed)
        assertEquals("Dad must approve SPEND time first.", o.refused)
        assertTrue(fs.commits.isEmpty())
    }

    @Test fun pausingAPausedFlightChangesNothing() {
        flying(bank = 3000, session = 1200, sinceChargeMs = 1_000)
        session().perform(SessionRules.PAUSE)
        val o = session().perform(SessionRules.PAUSE)
        assertFalse(o.changed)
        assertNull(o.refused)
        assertEquals(1, fs.commits.size)
    }

    @Test fun contentionIsRetriedAndAnExpiredTokenIsRefreshed() {
        flying(bank = 3000, session = 1200, sinceChargeMs = 5_000)
        val s = session()
        s.perform(SessionRules.PAUSE)
        fs.expireToken()                                          // the cached id token stops working
        fs.failNextWith = 409                                     // and the next call hits contention
        val o = s.perform(SessionRules.START)
        assertTrue(o.changed)
        assertEquals(true, fs.doc["running"])
        assertEquals(2, fs.tokenRequests)
    }

    @Test fun pauseRightAfterTheCockpitsCalibrationChargesTheWholeGap() {
        // Kit's review: the cockpit reopened 300 s after the last charge; its calibration charge wrote 0 s and
        // moved lastChargedAt, leaving the gap in its carry. It published that charge before committing it.
        flying(bank = 3000, session = 1200, sinceChargeMs = 400)            // the calibration landed 0.4 s ago
        val calibratedAt = fs.doc["lastChargedAt"] as Long
        val pending = Carry.Pending(fromMs = calibratedAt - 300_000, carryIn = 0.0, spent = 0, bankSeconds = 3000, sessionSeconds = 1200)
        session().perform(SessionRules.PAUSE, Carry(atMs = null, seconds = 0.0, pending = pending))
        assertEquals(0, fs.refusedCommits)
        assertEquals(2700L, fs.doc["bankSeconds"])                         // 300 s gap + 0.4 s since
        assertEquals(900L, fs.doc["sessionSeconds"])
    }

    @Test fun aCarryForAnOlderChargeIsIgnored() {
        flying(bank = 3000, session = 1200, sinceChargeMs = 10_000)
        session().perform(SessionRules.PAUSE, Carry(atMs = 12345L, seconds = 250.0,
            pending = Carry.Pending(fromMs = 1L, carryIn = 0.0, spent = 0, bankSeconds = 9999, sessionSeconds = 9999)))
        assertEquals(2990L, fs.doc["bankSeconds"])                         // only the 10 s actually since the last charge
    }

    @Test fun readShowsTheBankAsTheCockpitDoes() {
        flying(bank = 3000, session = 1200, sinceChargeMs = 61_500)
        val d = session().read()
        assertEquals(2939L, d.bankSeconds)
        assertEquals(1139L, d.sessionSeconds)
        assertTrue(d.running)
    }
}
