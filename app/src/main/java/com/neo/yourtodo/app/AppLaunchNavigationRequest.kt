package com.neo.yourtodo.app

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.app.push.PushNotificationContract
import com.neo.yourtodo.feature.calendar.api.CalendarDateRoute
import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.feature.calendar.api.CalendarWidgetIntentContract
import com.neo.yourtodo.feature.friends.api.FriendsRoute
import java.time.LocalDate

data class AppLaunchNavigationRequest(
    val id: Long,
    val topLevelRoute: NavKey,
    val contentRoute: NavKey? = null,
    val syncOnOpen: Boolean = false
)

fun parseAppLaunchNavigationRequest(
    intent: Intent?,
    requestId: Long
): AppLaunchNavigationRequest? {
    val pushRequest = parsePushNavigationRequest(
        action = intent?.action,
        pushType = intent?.getStringExtra(PushNotificationContract.EXTRA_TYPE),
        deepLink = intent?.getStringExtra(PushNotificationContract.EXTRA_DEEP_LINK),
        dataScheme = intent?.data?.scheme,
        requestId = requestId
    )
    if (pushRequest != null) return pushRequest

    return parseAppLaunchNavigationRequest(
        action = intent?.action,
        selectedDate = intent?.getStringExtra(CalendarWidgetIntentContract.EXTRA_SELECTED_DATE),
        requestId = requestId
    )
}

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

fun parseAppLaunchNavigationRequest(
    action: String?,
    selectedDate: String?,
    pushType: String?,
    deepLink: String?,
    dataScheme: String?,
    requestId: Long
): AppLaunchNavigationRequest? =
    parsePushNavigationRequest(
        action = action,
        pushType = pushType,
        deepLink = deepLink,
        dataScheme = dataScheme,
        requestId = requestId
    ) ?: parseAppLaunchNavigationRequest(
        action = action,
        selectedDate = selectedDate,
        requestId = requestId
    )

private fun parsePushNavigationRequest(
    action: String?,
    pushType: String?,
    deepLink: String?,
    dataScheme: String?,
    requestId: Long
): AppLaunchNavigationRequest? {
    if (
        action != PushNotificationContract.ACTION_OPEN_PUSH_NOTIFICATION &&
        dataScheme != "yourtodo" &&
        pushType.isNullOrBlank() &&
        deepLink.isNullOrBlank()
    ) {
        return null
    }

    return AppLaunchNavigationRequest(
        id = requestId,
        topLevelRoute = FriendsRoute,
        syncOnOpen = true
    )
}
