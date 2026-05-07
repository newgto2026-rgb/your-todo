package com.neo.yourtodo.feature.calendar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class GlanceCalendarWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) : CalendarWidgetUpdater {
    override suspend fun updateCalendarWidgets(): Result<Unit> = runCatching {
        CalendarMonthWidgetInstances.updateAll(context)
    }
}
