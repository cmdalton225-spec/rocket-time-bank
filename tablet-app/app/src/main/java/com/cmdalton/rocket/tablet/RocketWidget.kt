package com.cmdalton.rocket.tablet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.cmdalton.rocket.tablet.ui.MainActivity

class RocketWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetActions.ACTION_PAUSE, WidgetActions.ACTION_PLAY -> {
                WidgetActions.perform(context, intent.action!!, goAsync())
                return
            }
            WidgetRefreshScheduler.ACTION_TICK -> {
                refresh(context)
                WidgetActions.syncFromServer(context, goAsync())
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        paint(manager, ids, context)
        WidgetActions.syncFromServer(context, goAsync())
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshScheduler.cancel(context)
        super.onDisabled(context)
    }

    private fun paint(manager: AppWidgetManager, ids: IntArray, context: Context) {
        val views = buildViews(context, WidgetState.loadRaw(context))
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        /**
         * [raw] = the state as last saved; it is extrapolated to now here ([slackMs] lets the time's-up alarm,
         * which fires on the dot, land a flight whose last whole-second push said "1 s left").
         */
        fun buildViews(context: Context, raw: WidgetState, slackMs: Long = 0): RemoteViews {
            val now = System.currentTimeMillis()
            val state = raw.withElapsedSession(now + slackMs)
            val plan = WidgetPlan.view(state, SystemClock.elapsedRealtime(), raw.remainingMs(now), raw.chronoBase)
            val v = RemoteViews(context.packageName, R.layout.widget_rocket)
            if (plan.ticking) {
                v.setChronometer(R.id.widget_chrono, plan.chronoBase, null, true)
                v.setChronometerCountDown(R.id.widget_chrono, true)
                v.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
                v.setViewVisibility(R.id.widget_clock, View.GONE)
            } else {
                v.setChronometer(R.id.widget_chrono, SystemClock.elapsedRealtime(), null, false)
                v.setViewVisibility(R.id.widget_chrono, View.GONE)
                v.setViewVisibility(R.id.widget_clock, View.VISIBLE)
                v.setTextViewText(R.id.widget_clock, plan.clock)
            }
            v.setTextViewText(R.id.widget_label, plan.label)
            when (plan.button) {
                WidgetView.Button.NONE -> v.setViewVisibility(R.id.widget_action, View.GONE)
                else -> {
                    val pause = plan.button == WidgetView.Button.PAUSE
                    v.setViewVisibility(R.id.widget_action, View.VISIBLE)
                    v.setImageViewResource(R.id.widget_action, if (pause) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                    v.setContentDescription(R.id.widget_action, context.getString(if (pause) R.string.pause else R.string.play))
                    v.setOnClickPendingIntent(R.id.widget_action, action(context, if (pause) WidgetActions.ACTION_PAUSE else WidgetActions.ACTION_PLAY))
                }
            }
            // Always Asher's request/play cockpit, even if Dad Control was the last page open.
            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_KID_COCKPIT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, pi)
            return v
        }

        private fun action(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                if (action == WidgetActions.ACTION_PAUSE) 4201 else 4202,
                Intent(context, RocketWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun refresh(context: Context, slackMs: Long = 0) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, RocketWidget::class.java))
            if (ids.isEmpty()) return
            val raw = WidgetState.loadRaw(context)
            val views = buildViews(context, raw, slackMs)
            ids.forEach { manager.updateAppWidget(it, views) }
            WidgetRefreshScheduler.sync(context, raw.withElapsedSession(System.currentTimeMillis() + slackMs))
        }
    }
}
