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
    fun statusOnlyNotificationsDoNotOpenApp() {
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

            assertThat(opensApp).isFalse()
        }
    }
}
