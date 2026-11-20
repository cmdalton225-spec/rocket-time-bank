package com.cmdalton.rocket.tablet

import kotlin.math.abs

/** Pure timer decisions (no Android types), so they run as plain JVM unit tests. */
object FlightTimerPlan {
    /** A running flight whose projected end moved by more than this gets its timer rescheduled. */
    const val TOLERANCE_MS = 3_000L

    /** Seconds the flight can still run: it stops when either the session or the bank runs out. */
    fun secondsLeft(bankSeconds: Long, sessionSeconds: Long): Long =
        minOf(bankSeconds, sessionSeconds).coerceAtLeast(0)

    /**
     * Does a running flight with [secondsLeft] at [nowMs] need its timer (re)scheduled, given the end
     * time already scheduled ([scheduledEndMs], 0 = none)? Same flight, same end: no — so reopening
     * the app, Dad Control and back, or the 1 s re-renders never stack a second timer. Dad added or
     * removed time: yes.
     */
    fun needsSchedule(scheduledEndMs: Long, nowMs: Long, secondsLeft: Long): Boolean {
        if (secondsLeft <= 0) return false
        if (scheduledEndMs <= 0) return true
        return abs(scheduledEndMs - (nowMs + secondsLeft * 1000)) > TOLERANCE_MS
    }
}
