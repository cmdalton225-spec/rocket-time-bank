package com.cmdalton.rocket

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/**
 * The tablet's own Clock timer (the notification-bar countdown Asher likes), started with the exact
 * time left. The rocket's countdown stays the truth: if the Clock app is missing, refuses, or the
 * timer is cancelled, the flight carries on.
 */
object ClockTimer {
    const val LABEL = "Rocket"

    fun intent(seconds: Long): Intent =
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceIn(1, 86_400).toInt())
            .putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
            // Ask the Clock app to start it without opening its screen. Samsung's Clock may still
            // open itself; that is fine, the timer is set either way.
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)

    /** Returns true when the Clock app accepted the request. */
    fun start(context: Context, remainingMs: Long): Boolean {
        val i = intent((remainingMs + 999) / 1000)
        if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(i)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
