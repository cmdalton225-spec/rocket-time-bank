package com.cmdalton.rocket

import android.app.Application
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.AlarmClock
import android.widget.TextView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cmdalton.rocket.bank.Bank
import com.cmdalton.rocket.ui.LandingActivity
import com.cmdalton.rocket.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Device-level checks on Robolectric (this build machine has no hardware virtualization for an
 * emulator): the real activities, Compose UI, flight service, widget and Clock-timer intent,
 * on a Galaxy Tab A11+-sized landscape screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-hdpi")
class DeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private var clock = 1_800_000_000_000L

    @Before fun fresh() {
        app.getSharedPreferences("rocket", Context.MODE_PRIVATE).edit().clear().commit()
        RocketRepo.resetForTest()
        RocketRepo.clock = { clock }
        RocketRepo.init(app)
    }

    @After fun done() {
        RocketRepo.resetForTest()
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun typePin(pin: String) = pin.forEach { click(it.toString()) }

    private fun nextActivity(): Intent? = shadowOf(app).nextStartedActivity

    /** Drain started activities until one matches (other screens may open in between). */
    private fun findStarted(action: String? = null, cls: Class<*>? = null): Intent? {
        while (true) {
            val i = nextActivity() ?: return null
            if (action != null && i.action == action) return i
            if (cls != null && i.component?.className == cls.name) return i
        }
    }

    @Test fun bankSurvivesARestart() {
        RocketRepo.update { s, now -> Bank.addTime(s, 30, now) }
        RocketRepo.update { s, now -> Bank.request(s, 10, now) }
        RocketRepo.resetForTest() // like the app being killed or the tablet restarting
        RocketRepo.init(app)
        val s = RocketRepo.state.value
        assertEquals(30 * 60L, s.balanceSec)
        assertEquals(10, s.pendingRequestMin)
    }

    @Test fun pinIsSavedHashedAndChecked() {
        assertFalse(RocketRepo.hasPin)
        assertTrue(RocketRepo.setPin("1357"))
        assertTrue(RocketRepo.hasPin)
        assertTrue(RocketRepo.checkPin("1357"))
        assertFalse(RocketRepo.checkPin("1358"))
        val saved = app.getSharedPreferences("rocket", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(saved.contains("1357"))
    }

    @Test fun clockTimerIntentCarriesTheExactTimeAndSkipsUi() {
        val i = ClockTimer.intent(25 * 60L)
        assertEquals(AlarmClock.ACTION_SET_TIMER, i.action)
        assertEquals(1500, i.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertTrue(i.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
        assertTrue(ClockTimer.start(app, 25 * 60_000L))
        val sent = findStarted(action = AlarmClock.ACTION_SET_TIMER)!!
        assertEquals(1500, sent.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertTrue(sent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun missingClockAppIsHandledGracefully() {
        shadowOf(app).checkActivities(true) // no Clock app that handles SET_TIMER
        assertFalse(ClockTimer.start(app, 60_000L))
    }

    @Test fun widgetShowsTheSameBank() {
        val shadowManager = shadowOf(AppWidgetManager.getInstance(app))
        val id = shadowManager.createWidget(RocketWidget::class.java, R.layout.widget_rocket)
        RocketRepo.update { s, now -> Bank.addTime(s, 45, now) }
        val view = shadowManager.getViewFor(id)
        assertEquals("45 min", view.findViewById<TextView>(R.id.widget_minutes).text.toString())
        assertEquals("in the bank", view.findViewById<TextView>(R.id.widget_label).text.toString())

        RocketRepo.update { s, now -> Bank.request(s, 25, now) }
        RocketRepo.update { s, now -> Bank.approve(s, now) }
        assertEquals("25 min", shadowManager.getViewFor(id).findViewById<TextView>(R.id.widget_minutes).text.toString())
        RocketRepo.update { s, now -> Bank.play(s, now) }
        clock += 10 * 60_000L + 1
        RocketWidget.refresh(app)
        val flying = shadowManager.getViewFor(id)
        assertEquals("15 min", flying.findViewById<TextView>(R.id.widget_minutes).text.toString())
        assertEquals("flying", flying.findViewById<TextView>(R.id.widget_label).text.toString())
        // Tapping the widget opens the app.
        flying.performClick()
        assertNotNull(findStarted(cls = MainActivity::class.java))
    }

    @Test fun serviceLandsTheRocketAndOpensTheLandingScreen() {
        RocketRepo.update { s, now -> Bank.addTime(s, 10, now) }
        RocketRepo.update { s, now -> Bank.request(s, 5, now) }
        RocketRepo.update { s, now -> Bank.approve(s, now) }
        RocketRepo.update { s, now -> Bank.play(s, now) }
        val controller = Robolectric.buildService(FlightService::class.java).create().startCommand(0, 1)
        val service = controller.get()
        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertTrue(RocketRepo.state.value.flight!!.running)

        clock += 5 * 60_000L
        controller.startCommand(0, 2)
        val s = RocketRepo.state.value
        assertNull(s.flight)
        assertEquals(clock, s.landedAtMs)
        assertNotNull(findStarted(cls = LandingActivity::class.java))
        val nm = app.getSystemService(NotificationManager::class.java)
        val landing = shadowOf(nm).getNotification(FlightService.NOTE_LANDING)
        assertNotNull(landing)
        assertNotNull(landing.fullScreenIntent)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun screenOffPausesAndUnlockResumesWithAFreshClockTimer() {
        RocketRepo.update { s, now -> Bank.addTime(s, 30, now) }
        RocketRepo.update { s, now -> Bank.request(s, 20, now) }
        RocketRepo.update { s, now -> Bank.approve(s, now) }
        RocketRepo.update { s, now -> Bank.play(s, now) }
        Robolectric.buildService(FlightService::class.java).create().startCommand(0, 1)

        clock += 5 * 60_000L
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        val off = RocketRepo.state.value.flight!!
        assertFalse(off.running)
        assertTrue(off.autoPaused)
        assertEquals(15 * 60_000L, off.remainingMs)

        clock += 60 * 60_000L // an hour with the screen off burns nothing
        ShadowSettings.setCanDrawOverlays(true)
        while (nextActivity() != null) { /* drain */ }
        app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idle()
        val on = RocketRepo.state.value.flight!!
        assertTrue(on.running)
        assertEquals(15 * 60_000L, on.remainingAt(clock))
        val timer = findStarted(action = AlarmClock.ACTION_SET_TIMER)!!
        assertEquals(15 * 60, timer.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertTrue(RocketRepo.state.value.flight!!.clockSynced)
    }

    /** The spec's live-test loop, through the real screens: earn, request, approve, play, land. */
    @Test fun fullLoopThroughTheScreens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("0 min").assertExists()

            // Grown-up side: first use sets the PIN (typed twice), then adds earned time.
            click("Grown-ups")
            compose.onNodeWithText("Set a grown-up PIN").assertExists()
            typePin("2580")
            compose.onNodeWithText("Type the PIN again").assertExists()
            typePin("2580")
            click("+30 min")
            assertEquals(30 * 60L, RocketRepo.state.value.balanceSec)
            click("Done")

            // Kid side: pick 25 and ask.
            compose.onAllNodesWithText("30 min").onFirst().assertExists()
            click("25 min")
            click("Ask for 25 min")
            compose.onNodeWithText("Waiting for a grown-up to say OK").assertExists()
            assertEquals(25, RocketRepo.state.value.pendingRequestMin)

            // Wrong PIN keeps the grown-up side shut.
            click("Grown-ups")
            typePin("1111")
            compose.onNodeWithText("Wrong PIN").assertExists()
            typePin("2580")
            click("Approve")
            click("Done")

            // Play: the rocket's own countdown and the Clock timer both start.
            compose.onNodeWithText("25 min ready!").assertExists()
            while (nextActivity() != null) { /* drain */ }
            click("Play")
            val f = RocketRepo.state.value.flight!!
            assertTrue(f.running)
            val timer = findStarted(action = AlarmClock.ACTION_SET_TIMER)!!
            assertEquals(25 * 60, timer.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
            assertTrue(timer.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
            assertTrue(RocketRepo.state.value.flight!!.clockSynced)
            val svc = shadowOf(app).nextStartedService
            assertEquals(FlightService::class.java.name, svc.component!!.className)
            compose.onNodeWithText("25:00").assertExists()
            compose.onNodeWithText("Pause").assertExists()

            // Pause and resume with the big buttons.
            clock += 60_000L
            click("Pause")
            assertFalse(RocketRepo.state.value.flight!!.running)
            compose.onNodeWithText("24:00").assertExists()
            click("Play")
            assertTrue(RocketRepo.state.value.flight!!.running)

            // Time runs out: the landing screen.
            clock += 24 * 60_000L
            RocketRepo.update { s, now -> Bank.tick(s, now) }
            compose.waitForIdle()
            compose.onNodeWithText("The rocket has landed").assertExists()
            compose.onNodeWithText("OK").performClick()
            compose.waitForIdle()
            assertNull(RocketRepo.state.value.landedAtMs)
            compose.onAllNodesWithText("5 min").onFirst().assertExists()
        }
    }

    @Test fun clearMidFlightAsksFirstThenRefunds() {
        RocketRepo.update { s, now -> Bank.addTime(s, 20, now) }
        RocketRepo.update { s, now -> Bank.request(s, 20, now) }
        RocketRepo.update { s, now -> Bank.approve(s, now) }
        RocketRepo.update { s, now -> Bank.play(s, now) }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            clock += 8 * 60_000L
            click("Clear")
            compose.onNodeWithText("Land the rocket now?").assertExists()
            compose.onNodeWithText("Keep flying").performClick()
            compose.waitForIdle()
            assertNotNull(RocketRepo.state.value.flight)
            click("Clear")
            compose.onNodeWithText("Land now").performClick()
            compose.waitForIdle()
            assertNull(RocketRepo.state.value.flight)
            assertEquals(12 * 60L, RocketRepo.state.value.balanceSec)
        }
    }

    @Test fun landingActivityShowsAndReturnsToTheRocket() {
        RocketRepo.update { s, _ -> s.copy(landedAtMs = clock) }
        ActivityScenario.launch(LandingActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("The rocket has landed").assertExists()
            compose.onNodeWithText("OK").performClick()
            compose.waitForIdle()
            assertNull(RocketRepo.state.value.landedAtMs)
            assertNotNull(findStarted(cls = MainActivity::class.java))
        }
    }

    @Test fun noInternetPermission() {
        val info = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val perms = info.requestedPermissions?.toList() ?: emptyList()
        assertFalse(perms.contains(android.Manifest.permission.INTERNET))
        assertTrue(perms.contains("com.android.alarm.permission.SET_ALARM"))
    }
}
