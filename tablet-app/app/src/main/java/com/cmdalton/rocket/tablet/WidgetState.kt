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
    /** The flight's precise time left when saved (ms); drives the ticking widget countdown. */
    val remainingMsAtSave: Long = 0L,
    /** The countdown base on screen (SystemClock.elapsedRealtime ms), kept steady across syncs. */
    val chronoBase: Long = 0L,
) {
    /** The running flight's precise time left at [nowMillis] (0 when not running). */
    fun remainingMs(nowMillis: Long = System.currentTimeMillis()): Long {
        if (!running || savedAtMillis <= 0L) return 0
        val ms = if (remainingMsAtSave > 0) remainingMsAtSave else sessionSeconds * 1000L   // pre-17.4 saves
        return (ms - (nowMillis - savedAtMillis).coerceAtLeast(0)).coerceAtLeast(0)
    }

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
        var elapsed = ((nowMillis - savedAtMillis) / 1000).toInt().coerceAtLeast(0)
        // The precise countdown (when known) decides the moment the flight is over.
        if (remainingMsAtSave > 0 && remainingMs(nowMillis) <= 0) elapsed = maxOf(elapsed, sessionSeconds)
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
        private const val CARRY = "carry_json"

        fun load(context: Context): WidgetState = loadRaw(context).withElapsedSession()

        /** As last saved (not extrapolated): its savedAt / remainingMsAtSave / chronoBase drive the countdown. */
        fun loadRaw(context: Context): WidgetState {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return WidgetState()
            return try {
                fromJson(JSONObject(raw))
            } catch (_: Exception) {
                WidgetState()
            }
        }

        /** From the cockpit (tablet-bridge.js): whole seconds, pushed as the cockpit's clock changes. */
        fun save(context: Context, json: String) {
            val pushed = try {
                fromJson(JSONObject(json))
            } catch (_: Exception) {
                WidgetState()
            }
            store(context, pushed, WidgetPlan.remainingFromPush(pushed.sessionSeconds.toLong()))
        }

        /** The cockpit's unwritten flown time (see [Carry]), as kid-sync.js published it last. */
        fun saveCarry(context: Context, json: String) {
            val c = parseCarry(json) ?: return
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CARRY, carryJson(c)).apply()
        }

        fun loadCarry(context: Context): Carry? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CARRY, null)?.let { parseCarry(it) }

        fun parseCarry(json: String): Carry? = try {
            val o = JSONObject(json)
            val p = o.optJSONObject("pending")
            Carry(
                atMs = if (o.isNull("atMs")) null else o.getLong("atMs"),
                seconds = o.optDouble("sec", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                pending = p?.let { Carry.Pending(it.getLong("fromMs"), it.optDouble("carryIn", 0.0), it.getLong("spent"), it.getLong("bank"), it.getLong("session")) },
            )
        } catch (_: Exception) {
            null
        }

        private fun carryJson(c: Carry): String = JSONObject().apply {
            put("atMs", c.atMs ?: JSONObject.NULL)
            put("sec", c.seconds)
            c.pending?.let { put("pending", JSONObject().put("fromMs", it.fromMs).put("carryIn", it.carryIn).put("spent", it.spent)
                .put("bank", it.bankSeconds).put("session", it.sessionSeconds)) }
        }.toString()

        /**
         * Save [s] (balances as of now) with the flight's precise time left [remainingMs], keeping the countdown
         * already on screen when it is within a second of the truth (no visible jump on sync).
         */
        fun store(context: Context, s: WidgetState, remainingMs: Long) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = prefs.getString(KEY, null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
            val now = System.currentTimeMillis()
            val nowElapsed = android.os.SystemClock.elapsedRealtime()
            val base = if (s.running) WidgetPlan.base(previous?.takeIf { it.running }?.chronoBase ?: 0,
                WidgetPlan.freshBase(nowElapsed, remainingMs)) else 0L
            val merged = s.copy(savedAtMillis = now, remainingMsAtSave = if (s.running) remainingMs else 0, chronoBase = base)
            prefs.edit().putString(KEY, toJson(merged).toString()).apply()
            WidgetRefreshScheduler.sync(context, merged)
        }

        fun toJson(s: WidgetState) = JSONObject().apply {
            put("bankSeconds", s.bankSeconds)
            put("sessionSeconds", s.sessionSeconds)
            put("running", s.running)
            put("sessionStatus", s.sessionStatus)
            put("savedAtMillis", s.savedAtMillis)
            put("remainingMsAtSave", s.remainingMsAtSave)
            put("chronoBase", s.chronoBase)
        }

        fun fromJson(o: JSONObject) = WidgetState(
            bankSeconds = o.optInt("bankSeconds", 0),
            sessionSeconds = o.optInt("sessionSeconds", 0),
            running = o.optBoolean("running", false),
            sessionStatus = o.optString("sessionStatus", "idle"),
            savedAtMillis = o.optLong("savedAtMillis", 0L),
            remainingMsAtSave = o.optLong("remainingMsAtSave", 0L),
            chronoBase = o.optLong("chronoBase", 0L),
        )
    }
}