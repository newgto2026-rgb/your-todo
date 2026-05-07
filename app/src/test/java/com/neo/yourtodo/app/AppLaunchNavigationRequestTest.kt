package com.neo.yourtodo.app

import com.neo.yourtodo.feature.calendar.api.CalendarDateRoute
import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.feature.calendar.api.CalendarWidgetIntentContract
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLaunchNavigationRequestTest {

    @Test
    fun parseCalendarWidgetIntent_returnsCalendarDateRouteRequest() {
        val request = parseAppLaunchNavigationRequest(
            action = CalendarWidgetIntentContract.ACTION_OPEN_CALENDAR_DATE,
            selectedDate = "2026-05-07",
            requestId = 7L
        )

        assertThat(request).isEqualTo(
            AppLaunchNavigationRequest(
                id = 7L,
                topLevelRoute = CalendarRoute,
                contentRoute = CalendarDateRoute("2026-05-07")
            )
        )
    }

    @Test
    fun parseCalendarWidgetIntent_withoutValidDateFallsBackToCalendarTab() {
        val request = parseAppLaunchNavigationRequest(
            action = CalendarWidgetIntentContract.ACTION_OPEN_CALENDAR_DATE,
            selectedDate = "not-a-date",
            requestId = 8L
        )

        assertThat(request).isEqualTo(
            AppLaunchNavigationRequest(
                id = 8L,
                topLevelRoute = CalendarRoute,
                contentRoute = null
            )
        )
    }

    @Test
    fun parseLauncherIntent_returnsNull() {
        val request = parseAppLaunchNavigationRequest(
            action = "android.intent.action.MAIN",
            selectedDate = null,
            requestId = 9L
        )

        assertThat(request).isNull()
    }
}
