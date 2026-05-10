package com.neo.yourtodo.app

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.app.push.PushNotificationContract
import com.neo.yourtodo.feature.calendar.api.CalendarDateRoute
import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.feature.calendar.api.CalendarWidgetIntentContract
import com.neo.yourtodo.feature.friends.api.FriendsIncomingAssignmentRoute
import com.neo.yourtodo.feature.friends.api.FriendsRoute
import java.net.URI
import java.time.LocalDate

private const val PushTypeAssignmentBundleReceived = "ASSIGNMENT_BUNDLE_RECEIVED"

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
    val calendarRequest = parseAppLaunchNavigationRequest(
        action = intent?.action,
        selectedDate = intent?.getStringExtra(CalendarWidgetIntentContract.EXTRA_SELECTED_DATE),
        requestId = requestId
    )
    if (calendarRequest != null) return calendarRequest

    val pushRequest = parsePushNavigationRequest(
        action = intent?.action,
        pushType = intent?.getStringExtra(PushNotificationContract.EXTRA_TYPE),
        deepLink = intent?.getStringExtra(PushNotificationContract.EXTRA_DEEP_LINK),
        bundleId = intent?.getStringExtra(PushNotificationContract.EXTRA_BUNDLE_ID),
        actorUserId = intent?.getStringExtra(PushNotificationContract.EXTRA_ACTOR_USER_ID),
        actorNickname = intent?.getStringExtra(PushNotificationContract.EXTRA_ACTOR_NICKNAME),
        dataScheme = intent?.data?.scheme,
        dataString = intent?.dataString,
        requestId = requestId
    )
    return pushRequest
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
    bundleId: String? = null,
    actorUserId: String? = null,
    actorNickname: String? = null,
    dataScheme: String?,
    dataString: String? = null,
    requestId: Long
): AppLaunchNavigationRequest? =
    parseAppLaunchNavigationRequest(
        action = action,
        selectedDate = selectedDate,
        requestId = requestId
    ) ?: parsePushNavigationRequest(
        action = action,
        pushType = pushType,
        deepLink = deepLink,
        bundleId = bundleId,
        actorUserId = actorUserId,
        actorNickname = actorNickname,
        dataScheme = dataScheme,
        dataString = dataString,
        requestId = requestId
    )

private fun parsePushNavigationRequest(
    action: String?,
    pushType: String?,
    deepLink: String?,
    bundleId: String?,
    actorUserId: String?,
    actorNickname: String?,
    dataScheme: String?,
    dataString: String?,
    requestId: Long
): AppLaunchNavigationRequest? {
    if (action == CalendarWidgetIntentContract.ACTION_OPEN_CALENDAR_DATE) return null
    if (
        action != PushNotificationContract.ACTION_OPEN_PUSH_NOTIFICATION &&
        dataScheme != "yourtodo" &&
        pushType.isNullOrBlank() &&
        deepLink.isNullOrBlank()
    ) {
        return null
    }

    val parsedDeepLink = deepLink?.takeIf { it.isNotBlank() } ?: dataString
    if (!shouldOpenPushInApp(pushType = pushType, deepLink = parsedDeepLink)) {
        return null
    }
    val incomingAssignmentRoute = incomingAssignmentRoute(
        pushType = pushType,
        deepLink = parsedDeepLink,
        bundleId = bundleId,
        actorUserId = actorUserId,
        actorNickname = actorNickname,
        requestId = requestId
    )

    return AppLaunchNavigationRequest(
        id = requestId,
        topLevelRoute = FriendsRoute,
        contentRoute = incomingAssignmentRoute,
        syncOnOpen = true
    )
}

private fun shouldOpenPushInApp(pushType: String?, deepLink: String?): Boolean =
    pushType == PushTypeAssignmentBundleReceived || deepLink.assignmentBundleIdOrNull() != null

private fun incomingAssignmentRoute(
    pushType: String?,
    deepLink: String?,
    bundleId: String?,
    actorUserId: String?,
    actorNickname: String?,
    requestId: Long
): FriendsIncomingAssignmentRoute? {
    val parsedBundleId = bundleId?.takeIf { it.isNotBlank() }
        ?: deepLink.assignmentBundleIdOrNull()
    val parsedActorUserId = actorUserId?.takeIf { it.isNotBlank() }
    val parsedActorNickname = actorNickname?.takeIf { it.isNotBlank() }
    if (
        parsedBundleId == null &&
        parsedActorUserId == null &&
        pushType != PushTypeAssignmentBundleReceived
    ) {
        return null
    }
    return FriendsIncomingAssignmentRoute(
        friendUserId = parsedActorUserId,
        friendNickname = parsedActorNickname,
        bundleId = parsedBundleId,
        requestId = requestId
    )
}

private fun String?.assignmentBundleIdOrNull(): String? {
    val uri = this?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    val segments = uri.path
        ?.trim('/')
        ?.split('/')
        ?.filter { it.isNotBlank() }
        .orEmpty()
    return when {
        uri.scheme == "yourtodo" &&
            uri.host == "assignment-bundles" &&
            segments.size >= 2 &&
            segments[0] == "received" -> segments[1]

        else -> null
    }
}
