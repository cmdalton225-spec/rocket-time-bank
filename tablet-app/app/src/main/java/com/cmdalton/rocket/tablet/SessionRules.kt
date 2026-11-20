package com.cmdalton.rocket.tablet

/** The timeBank/main fields a session action reads (v17 schema). */
data class BankDoc(
    val bankSeconds: Long,
    val sessionSeconds: Long,
    val running: Boolean,
    val sessionStatus: String,
    val lastChargedAtMs: Long?,
)

/**
 * Flown time the open cockpit (kid-sync.js) has not written yet, so a widget Pause still charges it.
 * [atMs]/[seconds]: the carry left after the charge that set lastChargedAt = [atMs].
 * [pending]: a charge the cockpit was committing; if it landed (the doc shows its balances and a later
 * lastChargedAt) what it left owed is worked out exactly as the cockpit's settleCharge() does. After a late
 * reopen this is the whole gap: the cockpit's calibration charge writes 0 s and advances lastChargedAt.
 */
data class Carry(val atMs: Long?, val seconds: Double, val pending: Pending? = null) {
    data class Pending(val fromMs: Long, val carryIn: Double, val spent: Long, val bankSeconds: Long, val sessionSeconds: Long)

    /** Seconds owed on top of the time since [doc]'s lastChargedAt. */
    fun owedFor(doc: BankDoc): Double {
        val at = doc.lastChargedAtMs ?: return 0.0
        if (atMs == at) return seconds
        val p = pending ?: return 0.0
        if (at > p.fromMs && doc.bankSeconds == p.bankSeconds && doc.sessionSeconds == p.sessionSeconds) {
            return (at - p.fromMs) / 1000.0 + p.carryIn - p.spent
        }
        return 0.0
    }
}

/** What a session action writes. bank/session null = unchanged (Play only flips the flight on). */
sealed class SessionWrite {
    object NoChange : SessionWrite()
    data class Refused(val message: String) : SessionWrite()
    data class Update(val bankSeconds: Long?, val sessionSeconds: Long?, val running: Boolean, val sessionStatus: String) : SessionWrite()
}

/**
 * The cockpit's Play / Pause transaction (`updateSession` in www/kid-sync.js), ported line for line so the
 * widget performs the SAME session change: same fields, same rules (firestore.rules validStart /
 * validChargeOrPause / validEnd), lastChargedAt + updatedAt = the server's request time.
 * Elapsed play time is measured on the server's clock ([serverNowMs]), never the tablet's.
 */
object SessionRules {
    const val START = "start"
    const val PAUSE = "pause"

    fun decide(action: String, doc: BankDoc, serverNowMs: Long, carry: Carry? = null): SessionWrite {
        val bank = doc.bankSeconds.coerceAtLeast(0)
        val session = doc.sessionSeconds.coerceAtLeast(0)
        val state = doc.sessionStatus.ifEmpty { "idle" }
        if (action == START) {
            if (doc.running) return SessionWrite.NoChange
            if (state !in setOf("approved", "paused") || bank <= 0 || session <= 0) {
                return SessionWrite.Refused("Dad must approve SPEND time first.")
            }
            return SessionWrite.Update(null, null, running = true, sessionStatus = "running")
        }
        require(action == PAUSE) { "unknown action $action" }
        if (!doc.running) return SessionWrite.NoChange
        val spent = minOf(elapsedSeconds(doc, serverNowMs, carry), bank, session)
        val remainingBank = bank - spent
        val remainingSession = session - spent
        val finished = remainingBank <= 0 || remainingSession <= 0
        return SessionWrite.Update(
            bankSeconds = remainingBank,
            sessionSeconds = if (finished) 0 else remainingSession,
            running = false,
            sessionStatus = if (finished) "idle" else "paused",
        )
    }

    /** Whole seconds flown since the last charge, on the server's clock, plus the cockpit's unwritten carry. */
    fun elapsedSeconds(doc: BankDoc, serverNowMs: Long, carry: Carry? = null): Long {
        val at = doc.lastChargedAtMs ?: return 0
        if (!doc.running) return 0
        val owed = carry?.owedFor(doc) ?: 0.0
        return kotlin.math.floor((serverNowMs - at) / 1000.0 + owed).toLong().coerceAtLeast(0)
    }

    /** The balances as the cockpit shows them right now (a running flight has used time since its last charge). */
    fun effective(doc: BankDoc, serverNowMs: Long, carry: Carry? = null): BankDoc {
        val spent = minOf(elapsedSeconds(doc, serverNowMs, carry), doc.bankSeconds.coerceAtLeast(0), doc.sessionSeconds.coerceAtLeast(0))
        return doc.copy(bankSeconds = (doc.bankSeconds - spent).coerceAtLeast(0), sessionSeconds = (doc.sessionSeconds - spent).coerceAtLeast(0))
    }
}
