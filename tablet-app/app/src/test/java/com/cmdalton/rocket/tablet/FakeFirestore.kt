package com.cmdalton.rocket.tablet

import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.time.Instant

/**
 * A local stand-in for Firestore's REST API + Google's token endpoint, enforcing a port of the repo's
 * firestore.rules for timeBank/main child updates (validChildSessionUpdate: validStart /
 * validChargeOrPause / validEnd). The server keeps its OWN clock, [serverSkewMs] behind the tablet's.
 */
class FakeFirestore(private val serverSkewMs: Long = 5_000) : AutoCloseable {
    var doc = mutableMapOf<String, Any?>()
    val commits = mutableListOf<Map<String, Any?>>()
    var refusedCommits = 0
    var failNextWith: Int? = null          // e.g. 409 (contention) or 401 (expired token) once
    var tokenRequests = 0
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val base get() = "http://127.0.0.1:${server.address.port}"
    fun serverNow() = System.currentTimeMillis() - serverSkewMs
    private var validToken = "tok-1"

    init {
        server.createContext("/") { ex ->
            val path = ex.requestURI.path
            val body = ex.requestBody.bufferedReader().readText()
            val (code, out) = synchronized(this) { route(path, body, ex.requestHeaders.getFirst("Authorization")) }
            val bytes = out.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    private fun route(path: String, body: String, auth: String?): Pair<Int, String> {
        if (path == "/token") {
            tokenRequests++
            if (!body.contains("refresh_token=refresh-abc")) return 400 to """{"error":{"message":"INVALID_REFRESH_TOKEN"}}"""
            return 200 to JSONObject().put("id_token", validToken).put("user_id", "kid-uid").put("expires_in", "3600").toString()
        }
        failNextWith?.let { failNextWith = null; return it to """{"error":{"status":"${if (it == 409) "ABORTED" else "UNAUTHENTICATED"}"}}""" }
        if (auth != "Bearer $validToken") return 401 to """{"error":{"status":"UNAUTHENTICATED"}}"""
        return when {
            path.endsWith(":beginTransaction") -> 200 to """{"transaction":"tx-${commits.size}"}"""
            path.endsWith(":rollback") -> 200 to "{}"
            path.endsWith(":batchGet") -> 200 to JSONArray().put(JSONObject()
                .put("found", JSONObject().put("name", "projects/p/databases/(default)/documents/timeBank/main").put("fields", fields()))
                .put("readTime", iso(serverNow()))).toString()
            path.endsWith(":commit") -> commit(JSONObject(body))
            else -> 404 to "{}"
        }
    }

    private fun commit(req: JSONObject): Pair<Int, String> {
        val w = req.getJSONArray("writes").getJSONObject(0)
        val f = w.getJSONObject("update").getJSONObject("fields")
        val next = doc.toMutableMap()
        for (k in f.keys()) {
            val v = f.getJSONObject(k)
            next[k] = when {
                v.has("integerValue") -> v.getString("integerValue").toLong()
                v.has("booleanValue") -> v.getBoolean("booleanValue")
                else -> v.getString("stringValue")
            }
        }
        val now = serverNow()
        val transforms = w.optJSONArray("updateTransforms") ?: JSONArray()
        for (i in 0 until transforms.length()) {
            val t = transforms.getJSONObject(i)
            check(t.getString("setToServerValue") == "REQUEST_TIME")
            next[t.getString("fieldPath")] = now
        }
        if (!rulesAllow(doc, next, now)) {
            refusedCommits++
            return 403 to """{"error":{"status":"PERMISSION_DENIED"}}"""
        }
        doc = next
        commits += next
        return 200 to JSONObject().put("commitTime", iso(now)).toString()
    }

    /** firestore.rules, timeBank/main, isChild() branch. */
    private fun rulesAllow(old: Map<String, Any?>, new: Map<String, Any?>, requestTime: Long): Boolean {
        val allowed = setOf("bankSeconds", "sessionSeconds", "running", "sessionStatus", "lastChargedAt", "updatedAt")
        val affected = (old.keys + new.keys).filter { old[it] != new[it] }
        if (!allowed.containsAll(affected)) return false
        val nb = new["bankSeconds"] as? Long ?: return false
        val ns = new["sessionSeconds"] as? Long ?: return false
        val ob = old["bankSeconds"] as Long
        val os = old["sessionSeconds"] as Long
        if (nb < 0 || ns < 0 || nb > ob || ns > os) return false
        if (new["updatedAt"] != requestTime || new["lastChargedAt"] != requestTime) return false
        val oRun = old["running"] == true
        val nRun = new["running"] == true
        val oSt = old["sessionStatus"]
        val nSt = new["sessionStatus"]
        val validStart = !oRun && oSt in listOf("approved", "paused") && ob > 0 && os > 0 && nRun && nSt == "running" && nb == ob && ns == os
        val validChargeOrPause = oRun && oSt == "running" && (ob - nb) == (os - ns) &&
            ((nRun && nSt == "running" && nb > 0 && ns > 0) || (!nRun && nSt in listOf("paused", "idle")))
        val validEnd = !nRun && nSt == "idle" && ns == 0L &&
            ((oRun && oSt == "running") || (!oRun && oSt in listOf("approved", "paused") && nb == ob))
        return validStart || validChargeOrPause || validEnd
    }

    private fun fields(): JSONObject {
        val o = JSONObject()
        for ((k, v) in doc) {
            o.put(k, when (v) {
                is Long -> if (k == "lastChargedAt" || k == "updatedAt") JSONObject().put("timestampValue", iso(v))
                           else JSONObject().put("integerValue", v.toString())
                is Boolean -> JSONObject().put("booleanValue", v)
                else -> JSONObject().put("stringValue", v.toString())
            })
        }
        return o
    }

    fun expireToken() { validToken = "tok-${tokenRequests + 1}" }

    override fun close() = server.stop(0)

    companion object {
        fun iso(ms: Long): String = Instant.ofEpochMilli(ms).toString()
    }
}
