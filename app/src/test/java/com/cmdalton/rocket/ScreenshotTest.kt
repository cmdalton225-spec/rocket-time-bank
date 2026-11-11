package com.cmdalton.rocket

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cmdalton.rocket.bank.Bank
import com.cmdalton.rocket.bank.RocketState
import com.cmdalton.rocket.ui.MainActivity
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each kid screen at Galaxy Tab A11+ size (landscape) to build/screenshots so the layout
 * can be looked at without a tablet or emulator. Not an assertion test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-hdpi")
class ScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val clock = 1_800_000_000_000L
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots")

    @Before fun fresh() {
        app.getSharedPreferences("rocket", Context.MODE_PRIVATE).edit().clear().commit()
        RocketRepo.resetForTest()
        RocketRepo.clock = { clock }
        RocketRepo.init(app)
        outDir.mkdirs()
    }

    @After fun done() = RocketRepo.resetForTest()

    private fun shot(name: String, state: (RocketState, Long) -> RocketState) {
        RocketRepo.update(rule = state)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun pick() = shot("1-pick") { s, t -> Bank.addTime(s, 30, t) }

    @Test fun waiting() = shot("2-waiting") { s, t -> Bank.request(Bank.addTime(s, 30, t), 25, t) }

    @Test fun ready() = shot("3-ready") { s, t -> Bank.approve(Bank.request(Bank.addTime(s, 30, t), 25, t), t) }

    @Test fun flying() = shot("4-flying") { s, t ->
        Bank.tick(Bank.play(Bank.approve(Bank.request(Bank.addTime(s, 30, t), 25, t), t), t - 10 * 60_000L), t)
    }

    @Test fun pausedScreenOff() = shot("5-paused") { s, t ->
        Bank.pause(Bank.play(Bank.approve(Bank.request(Bank.addTime(s, 30, t), 25, t), t), t - 20 * 60_000L), t, auto = true)
    }

    @Test fun landed() = shot("6-landed") { s, t -> Bank.addTime(s, 5, t).copy(landedAtMs = t) }

    @Test fun empty() = shot("0-empty") { s, _ -> s }

    private fun save(name: String) {
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test fun grownUpSide() {
        RocketRepo.update { s, t -> Bank.request(Bank.addTime(s, 40, t), 25, t) }
        ActivityScenario.launch(MainActivity::class.java).use {
            tap("Grown-ups")
            save("7-set-pin")
            "1234".forEach { tap(it.toString()) }
            "1234".forEach { tap(it.toString()) }
            save("8-grown-ups")
        }
    }
}
