// ───────────────────────────────────────────────────────────────────
// NextSendWidgetProvider — the Home Screen "Next Message" widget
// ───────────────────────────────────────────────────────────────────
// A plain AppWidgetProvider + RemoteViews widget (no Jetpack Glance —
// this project has no Compose dependency in build.gradle.kts, and
// pulling one in just for a single-row text widget isn't worth the
// added build surface).
//
// HOW IT STAYS UP TO DATE:
// Android's own updatePeriodMillis fallback is clamped to a 30-minute
// floor, which isn't responsive enough for a "next send" countdown.
// Instead, MessageStore calls NextSendWidgetProvider.pushUpdate(context)
// directly whenever it writes a new next-send time or recipient name
// (see MessageStore.saveNextSendTime / clearNextSendTime /
// saveRecipientName), the same way SchedulerService already drives
// MessageStore's persistence. That keeps this provider a thin
// "read MessageStore, render RemoteViews" layer with no scheduling
// logic of its own.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

class NextSendWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val store = MessageStore(context)
        val nextSendTime = store.getNextSendTime()
        val recipientName = store.getRecipientName()

        val views = RemoteViews(context.packageName, R.layout.widget_next_send)
        views.setTextViewText(
            R.id.widget_time,
            NextSendFormatter.compactDisplayText(nextSendTime)
        )

        if (nextSendTime > 0L) {
            val recipient = recipientName.ifBlank { "your partner" }
            views.setTextViewText(R.id.widget_recipient, "to $recipient")
        } else {
            views.setTextViewText(R.id.widget_recipient, "")
        }

        // Tapping the widget opens the app, same as tapping the launcher icon.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_time, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_recipient, pendingIntent)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        /// Pushes a fresh render to every placed instance of this widget.
        /// Safe to call even if no instance is currently on a Home Screen
        /// (getAppWidgetIds then returns an empty array and this no-ops).
        @JvmStatic
        fun pushUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NextSendWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            for (appWidgetId in ids) {
                NextSendWidgetProvider().updateWidget(context, manager, appWidgetId)
            }
        }
    }
}
