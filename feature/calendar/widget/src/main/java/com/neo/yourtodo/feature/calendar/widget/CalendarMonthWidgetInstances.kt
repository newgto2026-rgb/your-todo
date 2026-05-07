package com.neo.yourtodo.feature.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver

internal object CalendarMonthWidgetInstances {
    val Compact: CalendarMonthWidget =
        CalendarMonthWidget(forcedLayout = CalendarMonthWidgetLayout.Compact)
    val Expanded: CalendarMonthWidget =
        CalendarMonthWidget(forcedLayout = CalendarMonthWidgetLayout.Expanded)

    suspend fun update(context: Context, glanceId: GlanceId) {
        widgetFor(context, glanceId).update(context, glanceId)
    }

    suspend fun updateAll(context: Context) {
        updateAllForReceiver(
            context = context,
            receiverClass = CalendarMonthWidgetReceiver::class.java,
            widget = Compact
        )
        updateAllForReceiver(
            context = context,
            receiverClass = CalendarMonthExpandedWidgetReceiver::class.java,
            widget = Expanded
        )
    }

    private fun widgetFor(context: Context, glanceId: GlanceId): CalendarMonthWidget {
        val glanceManager = GlanceAppWidgetManager(context)
        val appWidgetId = glanceManager.getAppWidgetId(glanceId)
        val provider = AppWidgetManager
            .getInstance(context)
            .getAppWidgetInfo(appWidgetId)
            ?.provider
            ?.className

        return if (provider == CalendarMonthExpandedWidgetReceiver::class.java.name) {
            Expanded
        } else {
            Compact
        }
    }

    private suspend fun updateAllForReceiver(
        context: Context,
        receiverClass: Class<out GlanceAppWidgetReceiver>,
        widget: GlanceAppWidget
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val glanceManager = GlanceAppWidgetManager(context)
        val receiver = ComponentName(context, receiverClass)

        appWidgetManager.getAppWidgetIds(receiver).forEach { appWidgetId ->
            widget.update(context, glanceManager.getGlanceIdBy(appWidgetId))
        }
    }
}
