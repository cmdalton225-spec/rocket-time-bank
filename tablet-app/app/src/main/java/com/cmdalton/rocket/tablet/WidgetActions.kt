package com.cmdalton.rocket.tablet

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Pause / Play from the home-screen widget, without opening the app. The widget runs the cockpit's own
 * session transaction ([SessionRules] via [FirestoreSession]) on Firestore — the one source of truth — then
 * shows what Firestore now holds and makes the flight timer follow it: paused = countdown notification and
 * time's-up alarm cancelled; playing = set for exactly the time left. The open cockpit (if any) sees the same
 * change through its own Firestore listener.
 */
object WidgetActions {
    const val ACTION_PAUSE = "com.cmdalton.rocket.tablet.action.WIDGET_PAUSE"
    const val ACTION_PLAY = "com.cmdalton.rocket.tablet.action.WIDGET_PLAY"

    /** All widget Firestore work runs here, one call at a time, in the order it was asked for. */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "rocket-widget") }

    /**
     * Bumped by every newer truth: a widget Pause / Play, or a push from the open cockpit. A background read
     * that started before the latest bump is stale and is dropped, never applied (Kit's review: a delayed
     * read must not undo a Pause or re-arm / cancel the flight timer).
     */
    private val generation = AtomicLong()

    fun noteNewerState() { generation.incrementAndGet() }

    fun currentGeneration(): Long = generation.get()

    /** Should a read that started at generation [startedAt] still be applied? */
    fun readIsCurrent(startedAt: Long): Boolean = generation.get() == startedAt

    /** Where an outcome lands; Android-backed in the app, recorded in tests. */
    interface Port {
        fun saveState(state: WidgetState, remainingMs: Long)
        fun timerSync(seconds: Long)
        fun timerCancel()
        fun refreshWidget()
    }

    /** Show Firestore's answer on the widget and make the flight timer follow it. */
    fun applyTo(outcome: SessionOutcome, port: Port) {
        val d = outcome.doc
        val flying = d.running && d.bankSeconds > 0 && d.sessionSeconds > 0
        // A flight whose time is used up is over, whatever Firestore still says until the next charge.
        val status = if (flying) "running" else if (d.running) "idle" else d.sessionStatus
        port.saveState(
            WidgetState(d.bankSeconds.toInt(), d.sessionSeconds.toInt(), running = flying, sessionStatus = status),
            remainingMs = if (flying) d.sessionSeconds * 1000 else 0,
        )
        if (flying) port.timerSync(FlightTimerPlan.secondsLeft(d.bankSeconds, d.sessionSeconds)) else port.timerCancel()
        port.refreshWidget()
    }

    fun perform(context: Context, action: String, pending: BroadcastReceiver.PendingResult) {
        val app = context.applicationContext
        val creds = ChildCredentials.load(app.noBackupFilesDir)
        if (creds == null) {
            toast(app, app.getString(R.string.widget_needs_app))
            pending.finish()
            return
        }
        val sessionAction = if (action == ACTION_PAUSE) SessionRules.PAUSE else SessionRules.START
        noteNewerState()                                   // any read already in flight is now stale
        val carry = WidgetState.loadCarry(app)
        worker.execute {
            val result = runCatching { FirestoreSession(creds).perform(sessionAction, carry) }
            Handler(Looper.getMainLooper()).post {
                try {
                    noteNewerState()                       // and so is any read queued behind this action
                    result.onSuccess { o ->
                        applyTo(o, port(app))
                        o.refused?.let { toast(app, it) }
                    }.onFailure { e ->
                        toast(app, app.getString(R.string.widget_failed, e.message ?: e.javaClass.simpleName))
                        RocketWidget.refresh(app)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    /** Re-read the bank from Firestore (widget update / flight tick), so the widget is right with the app closed. */
    fun syncFromServer(context: Context, pending: BroadcastReceiver.PendingResult) {
        val app = context.applicationContext
        val creds = ChildCredentials.load(app.noBackupFilesDir)
        if (creds == null) {
            pending.finish()
            return
        }
        val startedAt = currentGeneration()
        val carry = WidgetState.loadCarry(app)
        worker.execute {
            val result = if (readIsCurrent(startedAt)) runCatching { FirestoreSession(creds).read(carry) } else null
            Handler(Looper.getMainLooper()).post {
                try {
                    // Only if nothing newer (a widget action, a cockpit push) happened since this read began.
                    if (readIsCurrent(startedAt)) result?.onSuccess { applyTo(SessionOutcome(it, changed = false), port(app)) }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun port(app: Context) = object : Port {
        override fun saveState(state: WidgetState, remainingMs: Long) = WidgetState.store(app, state, remainingMs)
        override fun timerSync(seconds: Long) { FlightTimer.sync(app, seconds) }
        override fun timerCancel() = FlightTimer.cancel(app)
        override fun refreshWidget() = RocketWidget.refresh(app)
    }

    private fun toast(app: Context, text: String) = Toast.makeText(app, text, Toast.LENGTH_LONG).show()
}
