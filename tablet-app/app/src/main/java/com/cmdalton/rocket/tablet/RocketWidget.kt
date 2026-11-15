package com.cmdalton.rocket.tablet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cmdalton.rocket.tablet.ui.MainActivity

class RocketWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = buildViews(context, WidgetState.load(context))
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun text(s: WidgetState): Pair<String, String> = when {
            s.running -> "${s.sessionMinutes()} min" to "flying"
            s.sessionStatus == "approved" || s.sessionStatus == "paused" ->
                "${s.sessionMinutes()} min" to "ready to play"
            else -> "${s.bankMinutes()} min" to "in the bank"
        }

        fun buildViews(context: Context, s: WidgetState): RemoteViews {
            val (big, small) = text(s)
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
            val views = buildViews(context, WidgetState.load(context))
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}