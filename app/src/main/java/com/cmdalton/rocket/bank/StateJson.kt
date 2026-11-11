package com.cmdalton.rocket.bank

import org.json.JSONArray
import org.json.JSONObject

/** The saved form of [RocketState]. Unknown or broken fields fall back to safe defaults. */
object StateJson {
    private const val VERSION = 1

    fun encode(s: RocketState): String {
        val o = JSONObject()
        o.put("v", VERSION)
        o.put("balanceSec", s.balanceSec)
        s.pendingRequestMin?.let { o.put("pendingRequestMin", it) }
        s.landedAtMs?.let { o.put("landedAtMs", it) }
        s.flight?.let { f ->
            val fo = JSONObject()
            fo.put("totalSec", f.totalSec)
            fo.put("remainingMs", f.remainingMs)
            fo.put("launched", f.launched)
            f.runningSinceMs?.let { fo.put("runningSinceMs", it) }
            f.heartbeatMs?.let { fo.put("heartbeatMs", it) }
            fo.put("autoPaused", f.autoPaused)
            fo.put("clockSynced", f.clockSynced)
            o.put("flight", fo)
        }
        val h = JSONArray()
        s.history.forEach { e ->
            h.put(JSONObject().put("at", e.atMs).put("kind", e.kind.name).put("sec", e.seconds))
        }
        o.put("history", h)
        return o.toString()
    }

    fun decode(text: String?): RocketState {
        if (text.isNullOrBlank()) return RocketState()
        return try {
            val o = JSONObject(text)
            val flight = o.optJSONObject("flight")?.let { fo ->
                Flight(
                    totalSec = fo.getLong("totalSec"),
                    remainingMs = fo.getLong("remainingMs"),
                    launched = fo.optBoolean("launched", false),
                    runningSinceMs = if (fo.has("runningSinceMs")) fo.getLong("runningSinceMs") else null,
                    heartbeatMs = if (fo.has("heartbeatMs")) fo.getLong("heartbeatMs") else null,
                    autoPaused = fo.optBoolean("autoPaused", false),
                    clockSynced = fo.optBoolean("clockSynced", false),
                )
            }
            val history = mutableListOf<HistoryEntry>()
            o.optJSONArray("history")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val kind = runCatching { HistoryKind.valueOf(e.getString("kind")) }.getOrNull() ?: continue
                    history += HistoryEntry(e.getLong("at"), kind, e.optLong("sec", 0))
                }
            }
            RocketState(
                balanceSec = o.optLong("balanceSec", 0).coerceAtLeast(0),
                pendingRequestMin = if (o.has("pendingRequestMin")) o.getInt("pendingRequestMin") else null,
                flight = flight,
                landedAtMs = if (o.has("landedAtMs")) o.getLong("landedAtMs") else null,
                history = history.takeLast(HISTORY_LIMIT),
            )
        } catch (e: Exception) {
            RocketState()
        }
    }
}
