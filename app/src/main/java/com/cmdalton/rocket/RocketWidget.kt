package com.cmdalton.rocket

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cmdalton.rocket.bank.RocketState
import com.cmdalton.rocket.bank.minutesLeft
import com.cmdalton.rocket.ui.MainActivity

/**
 * Home-screen widget: a window onto the same bank. Rocket + big minutes; the whole widget is one
 * big tap target that opens the app. No small controls on the widget itself.
 */
class RocketWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        RocketRepo.init(context)
        val views = views(context, RocketRepo.state.value, RocketRepo.now())
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        /** What the widget shows: the big number and the small line under it. */
        fun text(s: RocketState, nowMs: Long): Pair<String, String> {
            val f = s.flight
            return when {
                f != null && f.launched -> "${minutesLeft(f.remainingAt(nowMs))} min" to
                    (if (f.running) "flying" else "paused")
                f != null -> "${f.totalSec / 60} min" to "ready to fly"
                else -> "${s.balanceMin} min" to "in the bank"
            }
        }

        fun views(context: Context, s: RocketState, nowMs: Long): RemoteViews {
            val (big, small) = text(s, nowMs)
            val v = RemoteViews(context.packageName, R.layout.widget_rocket)
            v.setTextViewText(R.id.widget_minutes, big)
            v.setTextViewText(R.id.widget_label, small)
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, pi)
            return v
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, RocketWidget::class.java))
            if (ids.isEmpty()) return
            val views = views(context, RocketRepo.state.value, RocketRepo.now())
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
