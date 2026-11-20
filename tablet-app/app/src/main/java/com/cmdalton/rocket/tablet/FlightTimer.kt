package com.cmdalton.rocket.tablet

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import com.cmdalton.rocket.tablet.ui.LandingActivity
import com.cmdalton.rocket.tablet.ui.MainActivity

/**
 * The flight's countdown outside the app: an ongoing "Rocket flight" notification counting down in the
 * status bar, and an exact alarm that rings "time's up" when the flight's time runs out.
 *
 * Why not the Clock app timer (17.1 started one with ACTION_SET_TIMER): Android lets an app start a
 * Clock timer but gives it no way to stop a running one. ACTION_DISMISS_TIMER only dismisses timers
 * that are already ringing (or one named by a deep-link URI, which SET_TIMER never hands back), so a
 * paused, stopped or landed flight left the Clock timer counting down and ringing anyway (Matt's
 * Nov 20 test). A timer the app owns is cancelled the moment the flight pauses, stops or lands.
 */
object FlightTimer {
    private const val PREFS = "flight_timer"
    private const val KEY_END = "end_elapsed_ms"   // SystemClock.elapsedRealtime() base: immune to clock changes
    private const val CHANNEL_FLIGHT = "rocket_flight_countdown"
    private const val CHANNEL_TIME_UP = "rocket_time_up"
    private const val NOTE_FLIGHT = 7001
    private const val NOTE_TIME_UP = 7002
    private const val REQ_ALARM = 7101
    private const val REQ_OPEN = 7102
    private const val REQ_LANDING = 7103
    private const val RING_MS = 60_000L
    const val ACTION_TIME_UP = "com.cmdalton.rocket.tablet.action.TIME_UP"

    /**
     * A flight is running with [secondsLeft]: make sure exactly one timer ends then.
     * Returns false when notifications are blocked (the countdown can't show or ring).
     */
    fun sync(context: Context, secondsLeft: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        val prefs = prefs(context)
        if (FlightTimerPlan.needsSchedule(prefs.getLong(KEY_END, 0), now, secondsLeft)) {
            val end = now + secondsLeft * 1000
            prefs.edit().putLong(KEY_END, end).apply()
            notes(context).cancel(NOTE_TIME_UP)     // a new flight silences an old "time's up"
            schedule(context, end)
            showCountdown(context, System.currentTimeMillis() + secondsLeft * 1000)
        }
        return notes(context).areNotificationsEnabled()
    }

    /** The flight paused, stopped or landed: no countdown, no alarm. */
    fun cancel(context: Context) {
        prefs(context).edit().remove(KEY_END).apply()
        alarms(context).cancel(alarmIntent(context))
        notes(context).cancel(NOTE_FLIGHT)
    }

    /** The landing screen was acknowledged: stop the ringing. */
    fun silence(context: Context) {
        notes(context).cancel(NOTE_TIME_UP)
    }

    /** The alarm fired: the flight's time is up. */
    internal fun timeUp(context: Context) {
        prefs(context).edit().remove(KEY_END).apply()
        val n = notes(context)
        n.cancel(NOTE_FLIGHT)
        ensureChannels(context)
        val note = Notification.Builder(context, CHANNEL_TIME_UP)
            .setSmallIcon(R.drawable.ic_stat_rocket)
            .setContentTitle(context.getString(R.string.time_up_title))
            .setContentText(context.getString(R.string.time_up_body))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openApp(context))
            .setFullScreenIntent(landing(context), true)
            .setAutoCancel(true)
            .setTimeoutAfter(RING_MS)
            .build()
        note.flags = note.flags or Notification.FLAG_INSISTENT   // rings until tapped, OK'd, or a minute passes
        n.notify(NOTE_TIME_UP, note)
        RocketWidget.refresh(context, slackMs = 2_000)                 // the widget's countdown lands on the dot
    }

    private fun schedule(context: Context, endElapsed: Long) {
        val am = alarms(context)
        val pi = alarmIntent(context)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
        }
    }

    private fun showCountdown(context: Context, endWallMs: Long) {
        ensureChannels(context)
        val note = Notification.Builder(context, CHANNEL_FLIGHT)
            .setSmallIcon(R.drawable.ic_stat_rocket)
            .setContentTitle(context.getString(R.string.note_flying))
            .setContentText(context.getString(R.string.note_time_left))
            .setWhen(endWallMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(context))
            .build()
        notes(context).notify(NOTE_FLIGHT, note)
    }

    private fun ensureChannels(context: Context) {
        val n = notes(context)
        if (n.getNotificationChannel(CHANNEL_FLIGHT) == null) {
            n.createNotificationChannel(
                NotificationChannel(CHANNEL_FLIGHT, context.getString(R.string.channel_flight), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
        if (n.getNotificationChannel(CHANNEL_TIME_UP) == null) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            n.createNotificationChannel(
                NotificationChannel(CHANNEL_TIME_UP, context.getString(R.string.channel_landing), NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        setSound(
                            alarmSound,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        enableVibration(true)
                    },
            )
        }
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ_ALARM,
            Intent(context, FlightTimeUpReceiver::class.java).setAction(ACTION_TIME_UP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, REQ_OPEN,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_KID_COCKPIT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun landing(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, REQ_LANDING,
            Intent(context, LandingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)
    private fun notes(context: Context) = context.getSystemService(NotificationManager::class.java)
}
