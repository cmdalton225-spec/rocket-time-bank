package com.cmdalton.rocket.tablet

import android.content.Context
import org.json.JSONObject

/** Mirrors the v17 Firebase bank fields the WebView reports — one bank for app + widget. */
data class WidgetState(
    val bankSeconds: Int = 0,
    val sessionSeconds: Int = 0,
    val running: Boolean = false,
    val sessionStatus: String = "idle",
) {
    fun bankMinutes(): Int = (bankSeconds + 59) / 60
    fun sessionMinutes(): Int = (sessionSeconds + 59) / 60

    companion object {
        private const val PREFS = "rocket_tablet_widget"
        private const val KEY = "state_json"

        fun load(context: Context): WidgetState {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return WidgetState()
            return try {
                fromJson(JSONObject(raw))
            } catch (_: Exception) {
                WidgetState()
            }
        }

        fun save(context: Context, json: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
        }

        fun fromJson(o: JSONObject) = WidgetState(
            bankSeconds = o.optInt("bankSeconds", 0),
            sessionSeconds = o.optInt("sessionSeconds", 0),
            running = o.optBoolean("running", false),
            sessionStatus = o.optString("sessionStatus", "idle"),
        )
    }
}