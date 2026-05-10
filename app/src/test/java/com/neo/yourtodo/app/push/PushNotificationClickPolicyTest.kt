package com.neo.yourtodo.app.push

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PushNotificationClickPolicyTest {

    @Test
    fun receivedAssignmentNotificationOpensApp() {
        val opensApp = shouldOpenPushNotificationInApp(
            mapOf(
                PushNotificationContract.EXTRA_TYPE to "ASSIGNMENT_BUNDLE_RECEIVED",
                PushNotificationContract.EXTRA_DEEP_LINK to "yourtodo://assignment-bundles/received/bundle-id"
            )
        )

        assertThat(opensApp).isTrue()
    }

    @Test
    fun receivedAssignmentDeepLinkWithoutTypeOpensApp() {
        val opensApp = shouldOpenPushNotificationInApp(
            mapOf(
                PushNotificationContract.EXTRA_DEEP_LINK to "yourtodo://assignment-bundles/received/bundle-id"
            )
        )

        assertThat(opensApp).isTrue()
    }

    @Test
    fun statusNotificationsOpenAppForDefaultNavigation() {
        listOf(
            "ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED",
            "ASSIGNMENT_BUNDLE_FULLY_DECIDED",
            "ASSIGNED_TODO_COMPLETED",
            "ASSIGNED_TODO_REOPENED",
            "ASSIGNED_TODO_CANCELED"
        ).forEach { type ->
            val opensApp = shouldOpenPushNotificationInApp(
                mapOf(
                    PushNotificationContract.EXTRA_TYPE to type,
                    PushNotificationContract.EXTRA_DEEP_LINK to "yourtodo://assigned-todos/sent/assigned-id"
                )
            )

            assertThat(opensApp).isTrue()
        }
    }

    @Test
    fun requestCodeUsesNotificationEventIdWhenPresent() {
        val first = pushNotificationRequestCode(
            data = mapOf(PushNotificationContract.EXTRA_NOTIFICATION_EVENT_ID to "event-1"),
            fallbackNonce = 1L
        )
        val second = pushNotificationRequestCode(
            data = mapOf(PushNotificationContract.EXTRA_NOTIFICATION_EVENT_ID to "event-1"),
            fallbackNonce = 2L
        )

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun requestCodeSeparatesNotificationsWithoutEventId() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "ASSIGNED_TODO_COMPLETED",
            PushNotificationContract.EXTRA_ASSIGNED_TODO_ID to "assigned-1"
        )

        val first = pushNotificationRequestCode(data = data, fallbackNonce = 1L)
        val second = pushNotificationRequestCode(data = data, fallbackNonce = 2L)

        assertThat(first).isNotEqualTo(second)
    }
}
