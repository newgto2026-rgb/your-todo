package com.neo.yourtodo.feature.calendar.api

import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
data object CalendarRoute : NavKey

@Serializable
data class CalendarDateRoute(
    val selectedDate: String
) : NavKey

interface CalendarFeatureEntry : AppFeatureEntry

object CalendarWidgetIntentContract {
    const val ACTION_OPEN_CALENDAR_DATE = "com.neo.yourtodo.action.OPEN_CALENDAR_DATE"
    const val EXTRA_SELECTED_DATE = "selected_date"
}
