package com.cmdalton.rocket.tablet

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Keeps the home-screen widget current while a play session runs and the WebView is closed. */
object WidgetRefreshScheduler {
    const val ACTION_TICK = "com.cmdalton.rocket.tablet.action.WIDGET_TICK"
    private const val REQUEST_CODE = 4101
    private const val TICK_MS = 60_000L

    fun sync(context: Context, state: WidgetState) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tick = tickIntent(context)
        if (state.running && state.sessionSeconds > 0) {
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + TICK_MS,
                TICK_MS,
                tick,
            )
        } else {
            am.cancel(tick)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickIntent(context))
    }

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, RocketWidget::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}