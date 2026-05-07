package com.neo.yourtodo.app

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.feature.calendar.api.CalendarDateRoute
import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.feature.calendar.api.CalendarWidgetIntentContract
import java.time.LocalDate

data class AppLaunchNavigationRequest(
    val id: Long,
    val topLevelRoute: NavKey,
    val contentRoute: NavKey? = null
)

fun parseAppLaunchNavigationRequest(
    intent: Intent?,
    requestId: Long
): AppLaunchNavigationRequest? =
    parseAppLaunchNavigationRequest(
        action = intent?.action,
        selectedDate = intent?.getStringExtra(CalendarWidgetIntentContract.EXTRA_SELECTED_DATE),
        requestId = requestId
    )

fun parseAppLaunchNavigationRequest(
    action: String?,
    selectedDate: String?,
    requestId: Long
): AppLaunchNavigationRequest? {
    if (action != CalendarWidgetIntentContract.ACTION_OPEN_CALENDAR_DATE) return null
    val parsedSelectedDate = selectedDate
        ?.let { rawDate -> runCatching { LocalDate.parse(rawDate) }.getOrNull() }
        ?.toString()
    return AppLaunchNavigationRequest(
        id = requestId,
        topLevelRoute = CalendarRoute,
        contentRoute = parsedSelectedDate?.let(::CalendarDateRoute)
    )
}
