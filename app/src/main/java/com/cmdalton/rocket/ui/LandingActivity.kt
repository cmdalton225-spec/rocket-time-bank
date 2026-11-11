package com.cmdalton.rocket.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cmdalton.rocket.FlightService
import com.cmdalton.rocket.RocketRepo
import com.cmdalton.rocket.bank.Bank

/**
 * The full-screen "The rocket has landed" page, opened by the flight service over whatever Asher
 * is doing (or from the landing notification). OK goes back to the rocket.
 */
class LandingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RocketRepo.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            LandingScreen {
                RocketRepo.update { s, _ -> Bank.dismissLanding(s) }
                FlightService.cancelLandingNote(this)
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        }
    }
}
