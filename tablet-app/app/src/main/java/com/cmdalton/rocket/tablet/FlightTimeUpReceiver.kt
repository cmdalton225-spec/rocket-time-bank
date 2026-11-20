package com.cmdalton.rocket.tablet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The flight timer's alarm: ring "time's up". */
class FlightTimeUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FlightTimer.ACTION_TIME_UP) FlightTimer.timeUp(context)
    }
}
