package com.cmdalton.rocket

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import com.cmdalton.rocket.bank.Bank
import com.cmdalton.rocket.bank.minutesLeft
import com.cmdalton.rocket.ui.LandingActivity
import com.cmdalton.rocket.ui.MainActivity

/**
 * Keeps the rocket's countdown going while Asher uses other apps. Lives from launch until the
 * rocket lands or is cleared. Pauses on screen-off and resumes on unlock (Matt's choice), and
 * restarts the Clock timer on resume for the exact time left.
 */
class FlightService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastHeartbeatSave = 0L
    private var lastWidgetMinutes = -1L
    private var lastRunning: Boolean? = null
    private var receiverOn = false
    private var recoveredLanding = false

    private val tickRunnable = object : Runnable {
        override fun run() = tick()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> RocketRepo.update { s, now -> Bank.pause(s, now, auto = true) }
                Intent.ACTION_USER_PRESENT -> resumeAfterScreenOff()
                Intent.ACTION_SCREEN_ON -> {
                    val km = getSystemService(KeyguardManager::class.java)
                    if (km == null || !km.isKeyguardLocked) resumeAfterScreenOff()
                }
            }
            tick()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        RocketRepo.init(this)
        val before = RocketRepo.state.value
        val after = RocketRepo.update { s, now -> Bank.recover(s, now) }
        recoveredLanding = before.flight != null && after.flight == null && after.landedAtMs != null
        createChannels(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Screen on/off and unlock are system broadcasts; they still arrive with NOT_EXPORTED.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        receiverOn = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always go foreground first (Android requires it after startForegroundService).
        goForeground()
        if (recoveredLanding) {
            recoveredLanding = false
            showLanding()
        }
        tick()
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacks(tickRunnable)
        if (receiverOn) unregisterReceiver(screenReceiver)
        receiverOn = false
        super.onDestroy()
    }

    private fun resumeAfterScreenOff() {
        val before = RocketRepo.state.value.flight
        val after = RocketRepo.update { s, now -> Bank.autoResume(s, now) }.flight
        if (before?.running == false && after?.running == true) {
            // Android only lets a background app open the Clock when "Appear on top" is allowed.
            // Otherwise the app restarts the Clock timer the next time it is opened.
            if (Settings.canDrawOverlays(this) && ClockTimer.start(this, after.remainingAt(RocketRepo.now()))) {
                RocketRepo.update { s, _ -> Bank.markClockSynced(s) }
            }
        }
    }

    private fun tick() {
        handler.removeCallbacks(tickRunnable)
        val hadFlight = RocketRepo.state.value.flight != null
        val now = RocketRepo.now()
        val beat = now - lastHeartbeatSave >= 5_000
        val s = RocketRepo.update(refreshWidget = false) { st, t ->
            val ticked = Bank.tick(st, t)
            // Only save the heartbeat every few seconds; landing always saves.
            if (ticked.flight == null || beat) ticked else st
        }
        if (beat) lastHeartbeatSave = now

        val f = s.flight
        if (f == null || !f.launched) {
            RocketWidget.refresh(this)
            if (hadFlight && s.landedAtMs != null) showLanding()
            stopForegroundCompat()
            stopSelf()
            return
        }
        val mins = minutesLeft(f.remainingAt(now))
        if (mins != lastWidgetMinutes || f.running != lastRunning) {
            lastWidgetMinutes = mins
            RocketWidget.refresh(this)
        }
        if (f.running != lastRunning) {
            lastRunning = f.running
            goForeground()
        }
        if (f.running) {
            // Wake up right when the next whole second ends, so landing is on time.
            val delay = (f.remainingAt(now) % 1000).let { if (it == 0L) 1000L else it }
            handler.postDelayed(tickRunnable, delay.coerceIn(50, 1000))
        }
    }

    private fun goForeground() {
        val n = flightNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTE_FLIGHT, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTE_FLIGHT, n)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun flightNotification(): Notification {
        val f = RocketRepo.state.value.flight
        val now = RocketRepo.now()
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = Notification.Builder(this, CHANNEL_FLIGHT)
            .setSmallIcon(R.drawable.ic_stat_rocket)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
        if (f != null && f.running) {
            b.setContentText(getString(R.string.note_flying))
                .setWhen(now + f.remainingAt(now))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else if (f != null) {
            b.setContentText(getString(R.string.note_paused, minutesLeft(f.remainingAt(now))))
                .setShowWhen(false)
        }
        return b.build()
    }

    /** The rocket landed: the full-screen landing page, over whatever Asher is doing. */
    private fun showLanding() {
        val intent = Intent(this, LandingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, CHANNEL_LANDING)
            .setSmallIcon(R.drawable.ic_stat_rocket)
            .setContentTitle(getString(R.string.landed_title))
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(NOTE_LANDING, note)
        // With "Appear on top" allowed (or the app on screen) the page opens right away.
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // The notification above still opens it.
        }
    }

    companion object {
        const val CHANNEL_FLIGHT = "flight"
        const val CHANNEL_LANDING = "landing"
        const val NOTE_FLIGHT = 1
        const val NOTE_LANDING = 2

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_FLIGHT, context.getString(R.string.channel_flight), NotificationManager.IMPORTANCE_LOW,
                ),
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LANDING, context.getString(R.string.channel_landing), NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        @Volatile var alive = false
            private set

        /**
         * Start or poke the service after any change: it runs while a flight is launched and
         * stops itself (removing its notification) once the flight is over. Safe to call repeatedly.
         */
        fun ensureRunning(context: Context) {
            val launched = RocketRepo.state.value.flight?.launched == true
            if (!launched && !alive) return
            context.startForegroundService(Intent(context, FlightService::class.java))
        }

        fun cancelLandingNote(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTE_LANDING)
        }
    }
}
