package com.cmdalton.rocket.tablet

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/** Starts the tablet Clock app timer; rocket v17 countdown stays primary (spec). */
object ClockTimer {
    const val LABEL = "Rocket"

    fun intent(seconds: Long): Intent =
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceIn(1, 86_400).toInt())
            .putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)

    fun start(context: Context, seconds: Long): Boolean {
        val i = intent(seconds)
        if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(i)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}