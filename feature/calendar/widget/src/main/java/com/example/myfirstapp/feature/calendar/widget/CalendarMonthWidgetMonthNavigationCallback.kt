package com.example.myfirstapp.feature.calendar.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import dagger.hilt.android.EntryPointAccessors

class CalendarMonthWidgetMonthNavigationCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val monthDelta = parameters[CalendarMonthWidgetActionParameters.MonthDelta] ?: 0
        val clock = EntryPointAccessors
            .fromApplication(context, CalendarMonthWidgetEntryPoint::class.java)
            .clock()

        updateAppWidgetState(context, glanceId) { preferences ->
            val displayedMonth = preferences.displayedMonthOrCurrent(clock)
                .moveByWidgetMonthDelta(monthDelta)
            preferences.setDisplayedMonth(displayedMonth)
        }
        CalendarMonthWidget().update(context, glanceId)
    }
}
