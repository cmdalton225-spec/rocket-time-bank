package com.cmdalton.rocket.tablet

import android.content.Context
import org.json.JSONObject

/** Mirrors the v17 Firebase bank fields the WebView reports — one bank for app + widget. */
data class WidgetState(
    val bankSeconds: Int = 0,
    val sessionSeconds: Int = 0,
    val running: Boolean = false,
    val sessionStatus: String = "idle",
    /** Wall-clock time when sessionSeconds was last synced from the WebView (for widget countdown). */
    val savedAtMillis: Long = 0L,
) {
    fun bankMinutes(): Int = (bankSeconds + 59) / 60
    fun sessionMinutes(): Int = (sessionSeconds + 59) / 60

    /**
     * Extrapolate a flight on the widget between bridge updates. Elapsed play time is charged to
     * BOTH balances (the bank and the session, as the cockpit does), and a flight that has run out
     * is shown as finished — idle, not "0 min / flying" — so the widget falls back to the
     * remaining bank (Kit's review).
     */
    fun withElapsedSession(nowMillis: Long = System.currentTimeMillis()): WidgetState {
        if (!running || savedAtMillis <= 0L) return this
        val elapsed = ((nowMillis - savedAtMillis) / 1000).toInt().coerceAtLeast(0)
        // Bounded by BOTH balances, like kid-sync.js: a flight ends when either runs out
        // (Dad can cut the bank below a prepared session; Kit's review).
        val charged = minOf(elapsed, sessionSeconds.coerceAtLeast(0), bankSeconds.coerceAtLeast(0))
        val session = (sessionSeconds - charged).coerceAtLeast(0)
        val bank = (bankSeconds - charged).coerceAtLeast(0)
        if (session == 0 || bank == 0) {
            return copy(bankSeconds = bank, sessionSeconds = if (bank == 0) 0 else session,
                running = false, sessionStatus = "idle")
        }
        if (charged == 0) return this
        return copy(bankSeconds = bank, sessionSeconds = session)
    }

    companion object {
        private const val PREFS = "rocket_tablet_widget"
        private const val KEY = "state_json"

        fun load(context: Context): WidgetState {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return WidgetState()
            return try {
                fromJson(JSONObject(raw)).withElapsedSession()
            } catch (_: Exception) {
                WidgetState()
            }
        }

        fun save(context: Context, json: String) {
            val merged = try {
                val o = JSONObject(json)
                fromJson(o).copy(savedAtMillis = System.currentTimeMillis())
            } catch (_: Exception) {
                WidgetState(savedAtMillis = System.currentTimeMillis())
            }
            val out = JSONObject().apply {
                put("bankSeconds", merged.bankSeconds)
                put("sessionSeconds", merged.sessionSeconds)
                put("running", merged.running)
                put("sessionStatus", merged.sessionStatus)
                put("savedAtMillis", merged.savedAtMillis)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, out.toString()).apply()
            WidgetRefreshScheduler.sync(context, merged)
        }

        fun fromJson(o: JSONObject) = WidgetState(
            bankSeconds = o.optInt("bankSeconds", 0),
            sessionSeconds = o.optInt("sessionSeconds", 0),
            running = o.optBoolean("running", false),
            sessionStatus = o.optString("sessionStatus", "idle"),
            savedAtMillis = o.optLong("savedAtMillis", 0L),
        )
    }
}