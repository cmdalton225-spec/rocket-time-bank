package com.cmdalton.rocket.tablet

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * This tablet's Firebase sign-in, handed over by the cockpit page (tablet-bridge.js): the same anonymous,
 * Dad-approved child account the WebView uses, so Firestore's rules treat the widget exactly like the cockpit.
 * Kept in no-backup app storage (the WebView already keeps the same token in its own app storage).
 */
data class ChildCredentials(val apiKey: String, val projectId: String, val uid: String, val refreshToken: String) {
    fun toJson(): String = JSONObject().put("apiKey", apiKey).put("projectId", projectId).put("uid", uid)
        .put("refreshToken", refreshToken).toString()

    companion object {
        fun fromJson(json: String): ChildCredentials? = try {
            val o = JSONObject(json)
            ChildCredentials(o.getString("apiKey"), o.getString("projectId"), o.getString("uid"), o.getString("refreshToken"))
                .takeIf { it.apiKey.isNotBlank() && it.projectId.isNotBlank() && it.uid.isNotBlank() && it.refreshToken.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        fun load(dir: File): ChildCredentials? =
            File(dir, FILE).takeIf { it.exists() }?.readText()?.let { fromJson(it) }

        /** Returns true when stored (new or changed). */
        fun store(dir: File, json: String): Boolean {
            val creds = fromJson(json) ?: return false
            val f = File(dir, FILE)
            if (f.exists() && fromJson(f.readText()) == creds) return false
            f.writeText(creds.toJson())
            return true
        }

        private const val FILE = "firebase-child.json"
    }
}

/** A session action's result: the bank as it now stands, and whether this call changed it. */
data class SessionOutcome(val doc: BankDoc, val changed: Boolean, val refused: String? = null)

class SessionException(message: String) : Exception(message)

/**
 * Firestore over its REST API, for the home-screen widget: one transaction (read timeBank/main, decide with
 * [SessionRules], commit with server request-time stamps), retried on contention. No Firebase SDK needed.
 */
class FirestoreSession(
    private val creds: ChildCredentials,
    private val firestoreBase: String = "https://firestore.googleapis.com/v1",
    private val tokenUrl: String = "https://securetoken.googleapis.com/v1/token",
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val dbPath = "projects/${creds.projectId}/databases/(default)/documents"
    private val bankName = "$dbPath/timeBank/main"
    private var idToken: String? = null

    fun perform(action: String, attempts: Int = 3): SessionOutcome {
        var last: Exception? = null
        repeat(attempts) {
            try {
                return attempt(action)
            } catch (e: Contention) {
                last = e
            }
        }
        throw SessionException("Firestore was busy (${last?.message}); try again.")
    }

    /** The bank right now, with a running flight's used time taken off (server clock). */
    fun read(): BankDoc {
        val (doc, serverNow) = get(null)
        return SessionRules.effective(doc, serverNow())
    }

    private fun attempt(action: String): SessionOutcome {
        val tx = post("$firestoreBase/$dbPath:beginTransaction", JSONObject()).getString("transaction")
        val (doc, serverNow) = get(tx)
        return when (val w = SessionRules.decide(action, doc, serverNow())) {
            SessionWrite.NoChange -> {
                rollback(tx)
                SessionOutcome(SessionRules.effective(doc, serverNow()), changed = false)
            }
            is SessionWrite.Refused -> {
                rollback(tx)
                SessionOutcome(SessionRules.effective(doc, serverNow()), changed = false, refused = w.message)
            }
            is SessionWrite.Update -> {
                val fields = JSONObject()
                    .put("running", JSONObject().put("booleanValue", w.running))
                    .put("sessionStatus", JSONObject().put("stringValue", w.sessionStatus))
                val mask = JSONArray().put("running").put("sessionStatus")
                w.bankSeconds?.let { fields.put("bankSeconds", JSONObject().put("integerValue", it.toString())); mask.put("bankSeconds") }
                w.sessionSeconds?.let { fields.put("sessionSeconds", JSONObject().put("integerValue", it.toString())); mask.put("sessionSeconds") }
                val write = JSONObject()
                    .put("update", JSONObject().put("name", bankName).put("fields", fields))
                    .put("updateMask", JSONObject().put("fieldPaths", mask))
                    .put("updateTransforms", JSONArray()
                        .put(JSONObject().put("fieldPath", "lastChargedAt").put("setToServerValue", "REQUEST_TIME"))
                        .put(JSONObject().put("fieldPath", "updatedAt").put("setToServerValue", "REQUEST_TIME")))
                    .put("currentDocument", JSONObject().put("exists", true))
                val res = post("$firestoreBase/$dbPath:commit", JSONObject().put("writes", JSONArray().put(write)).put("transaction", tx))
                val committedAt = res.optString("commitTime").takeIf { it.isNotEmpty() }?.let { parseTime(it) }
                SessionOutcome(
                    BankDoc(
                        bankSeconds = w.bankSeconds ?: doc.bankSeconds,
                        sessionSeconds = w.sessionSeconds ?: doc.sessionSeconds,
                        running = w.running,
                        sessionStatus = w.sessionStatus,
                        lastChargedAtMs = committedAt,
                    ),
                    changed = true,
                )
            }
        }
    }

    /** Read the bank (inside [tx] if given) -> the doc and a server clock anchored on the read's readTime. */
    private fun get(tx: String?): Pair<BankDoc, () -> Long> {
        val body = JSONObject().put("documents", JSONArray().put(bankName))
        if (tx != null) body.put("transaction", tx)
        val arr = JSONArray(request("$firestoreBase/$dbPath:batchGet", body.toString()))
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val found = r.optJSONObject("found") ?: continue
            val readAt = parseTime(r.getString("readTime"))
            val local = clock()
            return parseDoc(found.getJSONObject("fields")) to { readAt + (clock() - local) }
        }
        throw SessionException("Dad must create the Time Bank first.")
    }

    private fun rollback(tx: String) {
        try { post("$firestoreBase/$dbPath:rollback", JSONObject().put("transaction", tx)) } catch (_: Exception) { }
    }

    private fun post(url: String, body: JSONObject): JSONObject = JSONObject(request(url, body.toString()))

    private fun request(url: String, body: String, retried: Boolean = false): String {
        val token = idToken ?: refreshIdToken()
        val (code, text) = http("POST", url, body, mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json"))
        if (code == 401 && !retried) {
            idToken = null
            return request(url, body, retried = true)
        }
        if (code == 409 || text.contains("\"ABORTED\"")) throw Contention("HTTP $code")
        if (code == 403) throw SessionException("Firestore said no (this tablet may need Dad to link it again).")
        if (code !in 200..299) throw SessionException("Firestore error $code")
        return text
    }

    private fun refreshIdToken(): String {
        val form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(creds.refreshToken, "UTF-8")
        val (code, text) = http("POST", "$tokenUrl?key=" + URLEncoder.encode(creds.apiKey, "UTF-8"), form,
            mapOf("Content-Type" to "application/x-www-form-urlencoded"))
        if (code !in 200..299) throw SessionException("Sign-in expired — open Asher's Rocket once.")
        val o = JSONObject(text)
        if (o.optString("user_id", creds.uid) != creds.uid) throw SessionException("Signed in as a different device.")
        return o.getString("id_token").also { idToken = it }
    }

    private class Contention(message: String) : Exception(message)

    companion object {
        /** Same origin the bundled cockpit runs on, so the request looks to Google exactly like the WebView's. */
        const val ORIGIN = "https://appassets.androidplatform.net"

        fun parseTime(s: String): Long = Instant.parse(s).toEpochMilli()

        fun parseDoc(f: JSONObject): BankDoc = BankDoc(
            bankSeconds = num(f.optJSONObject("bankSeconds")),
            sessionSeconds = num(f.optJSONObject("sessionSeconds")),
            running = f.optJSONObject("running")?.optBoolean("booleanValue", false) ?: false,
            sessionStatus = f.optJSONObject("sessionStatus")?.optString("stringValue", "idle") ?: "idle",
            lastChargedAtMs = f.optJSONObject("lastChargedAt")?.optString("timestampValue")?.takeIf { it.isNotEmpty() }?.let { parseTime(it) },
        )

        private fun num(v: JSONObject?): Long = when {
            v == null -> 0
            v.has("integerValue") -> v.getString("integerValue").toLongOrNull() ?: 0
            v.has("doubleValue") -> kotlin.math.floor(v.getDouble("doubleValue")).toLong()
            else -> 0
        }

        fun http(method: String, url: String, body: String, headers: Map<String, String>): Pair<Int, String> {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 8_000
                c.readTimeout = 8_000
                c.setRequestProperty("Referer", "$ORIGIN/")
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally {
                c.disconnect()
            }
        }
    }
}
