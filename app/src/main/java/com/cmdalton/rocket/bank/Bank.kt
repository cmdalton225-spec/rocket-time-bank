package com.cmdalton.rocket.bank

/**
 * The Time Bank, as plain data plus pure rules. No Android here, so every rule is unit-tested.
 *
 * Money is kept in whole seconds so an early landing can refund exactly what was not used.
 * Wall-clock times are passed in (nowMs) by the caller.
 */

/** The amounts Asher can ask for and Matt can add (Matt's choice, 2026-11-11). */
val PRESETS_MIN = listOf(5, 10, 15, 20, 25, 30)

/** If the flight's heartbeat is older than this, the app was not running: time stops at the heartbeat. */
const val STALE_HEARTBEAT_MS = 15_000L

enum class HistoryKind { ADDED, REQUESTED, CANCELLED, APPROVED, DENIED, LAUNCHED, LANDED, CLEARED }

/** One line of the parent's history. [seconds] is the amount the line is about. */
data class HistoryEntry(val atMs: Long, val kind: HistoryKind, val seconds: Long)

/**
 * An approved flight. It exists from approval until it lands or is cleared.
 * - [launched] false: approved, waiting for Asher's big play button.
 * - [runningSinceMs] non-null: counting down right now, since that moment.
 * - [remainingMs]: time left at the last checkpoint (launch, pause, or resume).
 */
data class Flight(
    val totalSec: Long,
    val remainingMs: Long,
    val launched: Boolean = false,
    val runningSinceMs: Long? = null,
    val heartbeatMs: Long? = null,
    val autoPaused: Boolean = false,
    val clockSynced: Boolean = false,
) {
    val running: Boolean get() = runningSinceMs != null

    fun remainingAt(nowMs: Long): Long {
        val since = runningSinceMs ?: return remainingMs
        return (remainingMs - (nowMs - since).coerceAtLeast(0)).coerceAtLeast(0)
    }
}

data class RocketState(
    val balanceSec: Long = 0,
    val pendingRequestMin: Int? = null,
    val flight: Flight? = null,
    /** Set when a flight lands; cleared when Asher taps OK on the landing screen. */
    val landedAtMs: Long? = null,
    val history: List<HistoryEntry> = emptyList(),
) {
    /** Whole banked minutes, as shown to Asher (rounded down). */
    val balanceMin: Long get() = balanceSec / 60

    fun canRequest(minutes: Int): Boolean =
        minutes > 0 && pendingRequestMin == null && flight == null && minutes * 60L <= balanceSec

    fun canApprove(): Boolean {
        val m = pendingRequestMin ?: return false
        return flight == null && m * 60L <= balanceSec
    }
}

const val HISTORY_LIMIT = 300

private fun RocketState.log(nowMs: Long, kind: HistoryKind, seconds: Long): RocketState =
    copy(history = (history + HistoryEntry(nowMs, kind, seconds)).takeLast(HISTORY_LIMIT))

object Bank {

    /** Parent: put earned minutes in the bank. */
    fun addTime(s: RocketState, minutes: Int, nowMs: Long): RocketState {
        if (minutes <= 0) return s
        return s.copy(balanceSec = s.balanceSec + minutes * 60L).log(nowMs, HistoryKind.ADDED, minutes * 60L)
    }

    /** Kid: ask for an amount. Only when nothing else is going on and the bank covers it. */
    fun request(s: RocketState, minutes: Int, nowMs: Long): RocketState {
        if (!s.canRequest(minutes)) return s
        return s.copy(pendingRequestMin = minutes).log(nowMs, HistoryKind.REQUESTED, minutes * 60L)
    }

    /** Parent: approve. The minutes leave the bank and become a flight waiting for play. */
    fun approve(s: RocketState, nowMs: Long): RocketState {
        if (!s.canApprove()) return s
        val m = s.pendingRequestMin!!
        val sec = m * 60L
        return s.copy(
            balanceSec = s.balanceSec - sec,
            pendingRequestMin = null,
            flight = Flight(totalSec = sec, remainingMs = sec * 1000),
            landedAtMs = null,
        ).log(nowMs, HistoryKind.APPROVED, sec)
    }

    /** Parent: deny. Nothing leaves the bank. */
    fun deny(s: RocketState, nowMs: Long): RocketState {
        val m = s.pendingRequestMin ?: return s
        return s.copy(pendingRequestMin = null).log(nowMs, HistoryKind.DENIED, m * 60L)
    }

    /** Kid: the big play button. Launches an approved flight or resumes a paused one. */
    fun play(s: RocketState, nowMs: Long): RocketState {
        val f = s.flight ?: return s
        if (f.running || f.remainingMs <= 0) return s
        val next = s.copy(
            flight = f.copy(
                launched = true, runningSinceMs = nowMs, heartbeatMs = nowMs,
                autoPaused = false, clockSynced = false,
            ),
        )
        return if (!f.launched) next.log(nowMs, HistoryKind.LAUNCHED, f.totalSec) else next
    }

    /** Kid's pause button ([auto] false) or the screen turning off ([auto] true). */
    fun pause(s: RocketState, nowMs: Long, auto: Boolean = false): RocketState {
        val f = s.flight ?: return s
        if (!f.running) return s
        return s.copy(
            flight = f.copy(
                remainingMs = f.remainingAt(nowMs), runningSinceMs = null, heartbeatMs = null,
                autoPaused = auto, clockSynced = false,
            ),
        )
    }

    /** The screen came back on: resume only a flight that the screen-off paused. */
    fun autoResume(s: RocketState, nowMs: Long): RocketState {
        val f = s.flight ?: return s
        if (f.running || !f.autoPaused) return s
        return play(s, nowMs)
    }

    /**
     * Kid's big CLEAR (Matt's choice): before launch it clears a pending request; on an approved
     * or flying rocket it lands now and the unused time goes back to the bank.
     */
    fun clear(s: RocketState, nowMs: Long): RocketState {
        s.pendingRequestMin?.let { m ->
            return s.copy(pendingRequestMin = null).log(nowMs, HistoryKind.CANCELLED, m * 60L)
        }
        val f = s.flight ?: return s
        val refundSec = f.remainingAt(nowMs) / 1000
        return s.copy(balanceSec = s.balanceSec + refundSec, flight = null)
            .log(nowMs, HistoryKind.CLEARED, refundSec)
    }

    /** Called every second while flying. Lands the rocket at zero. */
    fun tick(s: RocketState, nowMs: Long): RocketState {
        val f = s.flight ?: return s
        if (!f.running) return s
        if (f.remainingAt(nowMs) > 0) return s.copy(flight = f.copy(heartbeatMs = nowMs))
        return s.copy(flight = null, landedAtMs = nowMs).log(nowMs, HistoryKind.LANDED, f.totalSec)
    }

    /**
     * After the app was not running (tablet restarted, app killed): a running flight whose heartbeat
     * is stale stops at the heartbeat, so time burns only while the rocket was really flying.
     */
    fun recover(s: RocketState, nowMs: Long): RocketState {
        val f = s.flight ?: return s
        if (!f.running) return s
        val hb = f.heartbeatMs ?: f.runningSinceMs!!
        if (nowMs - hb <= STALE_HEARTBEAT_MS) return s
        if (f.remainingAt(hb) <= 0) {
            return s.copy(flight = null, landedAtMs = hb).log(hb, HistoryKind.LANDED, f.totalSec)
        }
        return pause(s, hb, auto = true)
    }

    fun markClockSynced(s: RocketState): RocketState {
        val f = s.flight ?: return s
        if (!f.running) return s
        return s.copy(flight = f.copy(clockSynced = true))
    }

    fun dismissLanding(s: RocketState): RocketState = s.copy(landedAtMs = null)
}

/** "12:05" or "1:02:05". Rounds up so the screen never shows 0:00 while still flying. */
fun formatCountdown(remainingMs: Long): String {
    val total = (remainingMs + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Whole minutes left, rounded up (for the widget). */
fun minutesLeft(remainingMs: Long): Long = (remainingMs + 59_999) / 60_000
