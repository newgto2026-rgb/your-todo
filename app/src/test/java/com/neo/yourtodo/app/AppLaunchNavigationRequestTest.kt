package com.neo.yourtodo.app

import com.neo.yourtodo.feature.calendar.api.CalendarDateRoute
import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.feature.calendar.api.CalendarWidgetIntentContract
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.app.push.PushNotificationContract
import com.neo.yourtodo.feature.friends.api.FriendsIncomingAssignmentRoute
import com.neo.yourtodo.feature.friends.api.FriendsRoute
import org.junit.Test

class AppLaunchNavigationRequestTest {

    @Test
    fun parseCalendarWidgetIntent_returnsCalendarDateRouteRequest() {
        val request = parseAppLaunchNavigationRequest(
            action = CalendarWidgetIntentContract.ACTION_OPEN_CALENDAR_DATE,
            selectedDate = "2026-05-07",
            pushType = null,
            deepLink = null,
            actorNickname = null,
            dataScheme = "yourtodo",
            dataString = "yourtodo://calendar/date/2026-05-07",
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

    @Test
    fun parsePushIntent_returnsFriendsIncomingAssignmentRouteAndRequestsSync() {
        val request = parseAppLaunchNavigationRequest(
            action = PushNotificationContract.ACTION_OPEN_PUSH_NOTIFICATION,
            selectedDate = null,
            pushType = "ASSIGNMENT_BUNDLE_RECEIVED",
            deepLink = "yourtodo://assignment-bundles/received/bundle-id",
            bundleId = null,
            actorUserId = "friend-user-id",
            actorNickname = "monday",
            dataScheme = null,
            requestId = 10L
        )

        assertThat(request).isEqualTo(
            AppLaunchNavigationRequest(
                id = 10L,
                topLevelRoute = FriendsRoute,
                contentRoute = FriendsIncomingAssignmentRoute(
                    friendUserId = "friend-user-id",
                    friendNickname = "monday",
                    bundleId = "bundle-id",
                    requestId = 10L
                ),
                syncOnOpen = true
            )
        )
    }

    @Test
    fun parseAssignmentReceivedPushWithoutIdentifiersStillRoutesToIncomingAssignments() {
        val request = parseAppLaunchNavigationRequest(
            action = PushNotificationContract.ACTION_OPEN_PUSH_NOTIFICATION,
            selectedDate = null,
            pushType = "ASSIGNMENT_BUNDLE_RECEIVED",
            deepLink = null,
            bundleId = null,
            actorUserId = null,
            actorNickname = null,
            dataScheme = null,
            requestId = 13L
        )

        assertThat(request).isEqualTo(
            AppLaunchNavigationRequest(
                id = 13L,
                topLevelRoute = FriendsRoute,
                contentRoute = FriendsIncomingAssignmentRoute(requestId = 13L),
                syncOnOpen = true
            )
        )
    }

    @Test
    fun parseYourTodoDeepLink_returnsFriendsRouteAndRequestsSync() {
        val request = parseAppLaunchNavigationRequest(
            action = "android.intent.action.VIEW",
            selectedDate = null,
            pushType = null,
            deepLink = null,
            actorNickname = null,
            dataScheme = "yourtodo",
            requestId = 11L
        )

        assertThat(request?.topLevelRoute).isEqualTo(FriendsRoute)
        assertThat(request?.syncOnOpen).isTrue()
    }

    @Test
    fun parseYourTodoAssignmentDeepLink_returnsIncomingAssignmentRoute() {
        val request = parseAppLaunchNavigationRequest(
            action = PushNotificationContract.ACTION_OPEN_PUSH_NOTIFICATION,
            selectedDate = null,
            pushType = null,
            deepLink = null,
            actorNickname = null,
            dataScheme = "yourtodo",
            dataString = "yourtodo://assignment-bundles/received/bundle-id",
            requestId = 12L
        )

        assertThat(request).isEqualTo(
            AppLaunchNavigationRequest(
                id = 12L,
                topLevelRoute = FriendsRoute,
                contentRoute = FriendsIncomingAssignmentRoute(
                    friendUserId = null,
                    bundleId = "bundle-id",
                    requestId = 12L
                ),
                syncOnOpen = true
            )
        )
    }

    @Test
    fun parseRepeatedPushIntent_returnsDistinctIncomingAssignmentRoutes() {
        val first = parseAppLaunchNavigationRequest(
            action = PushNotificationContract.ACTION_OPEN_PUSH_NOTIFICATION,
            selectedDate = null,
            pushType = "ASSIGNMENT_BUNDLE_RECEIVED",
            deepLink = "yourtodo://assignment-bundles/received/bundle-id",
            bundleId = null,
            actorUserId = null,
            actorNickname = null,
            dataScheme = null,
            requestId = 14L
        )
        val second = parseAppLaunchNavigationRequest(
            action = PushNotificationContract.ACTION_OPEN_PUSH_NOTIFICATION,
            selectedDate = null,
            pushType = "ASSIGNMENT_BUNDLE_RECEIVED",
            deepLink = "yourtodo://assignment-bundles/received/bundle-id",
            bundleId = null,
            actorUserId = null,
            actorNickname = null,
            dataScheme = null,
            requestId = 15L
        )

        assertThat(first?.contentRoute).isNotEqualTo(second?.contentRoute)
    }
}
