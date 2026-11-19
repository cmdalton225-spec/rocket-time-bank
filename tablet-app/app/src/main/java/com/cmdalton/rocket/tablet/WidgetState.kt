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

    /** Extrapolate play time on the widget between bridge updates. */
    fun withElapsedSession(nowMillis: Long = System.currentTimeMillis()): WidgetState {
        if (!running || sessionSeconds <= 0 || savedAtMillis <= 0L) return this
        val elapsed = ((nowMillis - savedAtMillis) / 1000).toInt().coerceAtLeast(0)
        if (elapsed == 0) return this
        return copy(sessionSeconds = (sessionSeconds - elapsed).coerceAtLeast(0))
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