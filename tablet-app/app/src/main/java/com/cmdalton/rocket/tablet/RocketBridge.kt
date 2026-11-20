package com.cmdalton.rocket.tablet

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import com.cmdalton.rocket.tablet.ui.LandingActivity
import com.cmdalton.rocket.tablet.ui.MainActivity

class RocketBridge(private val host: MainActivity) {
    @JavascriptInterface
    fun onBankState(json: String) {
        WidgetState.save(host, json)
        RocketWidget.refresh(host)
    }

    /** A flight is running: keep one status-bar countdown + time's-up alarm ending in [seconds]. */
    @JavascriptInterface
    fun syncFlightTimer(seconds: Long) {
        host.runOnUiThread {
            if (!FlightTimer.sync(host, seconds)) host.showTimerToast()
        }
    }

    /** The flight paused, stopped or landed: cancel the countdown and the alarm. */
    @JavascriptInterface
    fun cancelFlightTimer() {
        host.runOnUiThread { FlightTimer.cancel(host) }
    }

    @JavascriptInterface
    fun showLanding() {
        host.runOnUiThread {
            if (host.landingOpen) return@runOnUiThread
            host.landingOpen = true
            host.startActivity(
                Intent(host, LandingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    companion object {
        const val NAME = "RocketTablet"
    }
}