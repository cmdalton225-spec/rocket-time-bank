package com.cmdalton.rocket.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.cmdalton.rocket.ClockTimer
import com.cmdalton.rocket.FlightService
import com.cmdalton.rocket.R
import com.cmdalton.rocket.RocketRepo
import com.cmdalton.rocket.bank.Bank
import kotlinx.coroutines.delay

enum class Screen { KID, PIN, PARENT }

class MainActivity : ComponentActivity() {
    private var message by mutableStateOf<String?>(null)
    private var setupTick by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RocketRepo.init(this)
        FlightService.createChannels(this)
        askForNotifications()
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        RocketRepo.update { s, now -> Bank.recover(s, now) }
        FlightService.ensureRunning(this)
        syncClockTimer()
        setupTick++
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    /** A running flight whose Clock timer is out of date (after a pause) gets a fresh one. */
    private fun syncClockTimer() {
        val f = RocketRepo.state.value.flight ?: return
        if (!f.running || f.clockSynced) return
        if (ClockTimer.start(this, f.remainingAt(RocketRepo.now()))) {
            RocketRepo.update { s, _ -> Bank.markClockSynced(s) }
            message = null
        } else {
            message = getString(R.string.clock_failed)
        }
    }

    private fun play() {
        RocketRepo.update { s, now -> Bank.play(s, now) }
        FlightService.ensureRunning(this)
        syncClockTimer()
    }

    private fun kidActions(openGrownUps: () -> Unit) = KidActions(
        request = { m -> RocketRepo.update { s, now -> Bank.request(s, m, now) } },
        clear = {
            RocketRepo.update { s, now -> Bank.clear(s, now) }
            FlightService.ensureRunning(this)
            message = null
        },
        play = ::play,
        pause = {
            RocketRepo.update { s, now -> Bank.pause(s, now) }
            FlightService.ensureRunning(this)
        },
        openGrownUps = openGrownUps,
    )

    fun setupStatus(): Map<SetupItem, Boolean> {
        val nm = getSystemService(NotificationManager::class.java)
        return mapOf(
            SetupItem.NOTIFICATIONS to (nm?.areNotificationsEnabled() ?: true),
            SetupItem.OVERLAY to Settings.canDrawOverlays(this),
            SetupItem.FULL_SCREEN to
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || nm?.canUseFullScreenIntent() ?: true),
        )
    }

    private fun openSetting(item: SetupItem) {
        val pkg = Uri.parse("package:$packageName")
        val intent = when (item) {
            SetupItem.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            SetupItem.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            SetupItem.FULL_SCREEN ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                }
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        }
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        val s by RocketRepo.state.collectAsState()
        var screen by rememberSaveable { mutableStateOf(Screen.KID) }
        var now by remember { mutableLongStateOf(RocketRepo.now()) }
        val running = s.flight?.running == true
        LaunchedEffect(running) {
            now = RocketRepo.now()
            while (running) {
                delay(250)
                now = RocketRepo.now()
            }
        }
        BackHandler(enabled = screen != Screen.KID) { screen = Screen.KID }

        when {
            screen == Screen.PIN -> PinScreen(
                hasPin = RocketRepo.hasPin,
                setPin = { RocketRepo.setPin(it) },
                checkPin = { RocketRepo.checkPin(it) },
                onUnlocked = { screen = Screen.PARENT },
                onBack = { screen = Screen.KID },
            )
            screen == Screen.PARENT -> {
                val tick = setupTick // re-read permissions when coming back from Settings
                val setup = remember(tick) { setupStatus() }
                ParentScreen(
                    s, now, setup,
                    ParentActions(
                        approve = { RocketRepo.update { st, t -> Bank.approve(st, t) } },
                        deny = { RocketRepo.update { st, t -> Bank.deny(st, t) } },
                        add = { m -> RocketRepo.update { st, t -> Bank.addTime(st, m, t) } },
                        openSetting = ::openSetting,
                        done = { screen = Screen.KID },
                    ),
                )
            }
            s.landedAtMs != null -> LandingScreen {
                RocketRepo.update { st, _ -> Bank.dismissLanding(st) }
                FlightService.cancelLandingNote(this)
            }
            else -> KidScreen(s, now, message, kidActions { screen = Screen.PIN })
        }
    }
}
