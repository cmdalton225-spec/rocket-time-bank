package com.cmdalton.rocket.tablet

import org.junit.Assert.assertEquals
import org.junit.Test

/** After a widget Pause / Play, the widget and the flight timer follow what Firestore now holds. */
class WidgetActionsTest {
    private class Recorder : WidgetActions.Port {
        val calls = mutableListOf<String>()
        var saved: WidgetState? = null
        var savedRemainingMs = -1L
        override fun saveState(state: WidgetState, remainingMs: Long) { saved = state; savedRemainingMs = remainingMs; calls += "save" }
        override fun timerSync(seconds: Long) { calls += "timer:$seconds" }
        override fun timerCancel() { calls += "timer:cancel" }
        override fun refreshWidget() { calls += "widget" }
    }

    @Test fun pauseCancelsTheCountdownAndAlarmAndFreezesTheWidget() {
        val r = Recorder()
        WidgetActions.applyTo(SessionOutcome(BankDoc(2958, 1158, false, "paused", 1L), changed = true), r)
        assertEquals(listOf("save", "timer:cancel", "widget"), r.calls)
        assertEquals(WidgetState(2958, 1158, false, "paused"), r.saved)
    }

    @Test fun playSetsTheTimerForExactlyTheTimeLeft() {
        val r = Recorder()
        WidgetActions.applyTo(SessionOutcome(BankDoc(2990, 1190, true, "running", 1L), changed = true), r)
        assertEquals(listOf("save", "timer:1190", "widget"), r.calls)
        assertEquals(1_190_000L, r.savedRemainingMs)
        assertEquals(WidgetState(2990, 1190, true, "running"), r.saved)
    }

    @Test fun playWhenTheBankIsShorterThanTheSessionEndsWithTheBank() {
        val r = Recorder()
        WidgetActions.applyTo(SessionOutcome(BankDoc(300, 600, true, "running", 1L), changed = true), r)
        assertEquals("timer:300", r.calls[1])
    }

    @Test fun aFlightWhoseTimeIsUsedUpShowsLandedAndCancelsTheTimer() {
        val r = Recorder()
        WidgetActions.applyTo(SessionOutcome(BankDoc(2970, 0, true, "running", 1L), changed = false), r)
        assertEquals(listOf("save", "timer:cancel", "widget"), r.calls)
        assertEquals(WidgetState(2970, 0, false, "idle"), r.saved)
    }
}
