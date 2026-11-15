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

    @JavascriptInterface
    fun startClockTimer(seconds: Long) {
        host.runOnUiThread {
            val ok = ClockTimer.start(host, seconds)
            if (!ok) host.showClockToast()
        }
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