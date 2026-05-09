package com.neo.yourtodo.app.push

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.R
import org.junit.Test

class PushNotificationMessageTest {
    @Test
    fun defaultMessage_mapsFakeFriendRequestPayload() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "FRIEND_REQUEST_RECEIVED",
            PushNotificationContract.EXTRA_DEEP_LINK to "yourtodo://friends/requests",
            PushNotificationContract.EXTRA_ACTOR_NICKNAME to "neo"
        )

        assertThat(PushNotificationMessage.defaultTitle(data))
            .isEqualTo(R.string.push_friend_request_title)
        assertThat(PushNotificationMessage.defaultBody(data))
            .isEqualTo(R.string.push_friend_request_body)
    }

    @Test
    fun defaultMessage_mapsFakeSharedTodoReceivedPayload() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "ASSIGNMENT_BUNDLE_RECEIVED",
            PushNotificationContract.EXTRA_DEEP_LINK to "yourtodo://assignment-bundles/received/bundle-id",
            PushNotificationContract.EXTRA_BUNDLE_ID to "bundle-id"
        )

        assertThat(PushNotificationMessage.defaultTitle(data))
            .isEqualTo(R.string.push_assignment_received_title)
        assertThat(PushNotificationMessage.defaultBody(data))
            .isEqualTo(R.string.push_assignment_received_body)
    }

    @Test
    fun defaultMessage_mapsFakeCompletionPayload() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "ASSIGNED_TODO_COMPLETED",
            PushNotificationContract.EXTRA_DEEP_LINK to "yourtodo://assigned-todos/sent/assigned-id",
            PushNotificationContract.EXTRA_ASSIGNED_TODO_ID to "assigned-id"
        )

        assertThat(PushNotificationMessage.defaultTitle(data))
            .isEqualTo(R.string.push_assigned_todo_completed_title)
        assertThat(PushNotificationMessage.defaultBody(data))
            .isEqualTo(R.string.push_assigned_todo_completed_body)
    }

    @Test
    fun defaultMessage_unknownPayloadFallsBackToGenericUpdate() {
        val data = mapOf(PushNotificationContract.EXTRA_TYPE to "UNKNOWN")

        assertThat(PushNotificationMessage.defaultTitle(data))
            .isEqualTo(R.string.push_default_title)
        assertThat(PushNotificationMessage.defaultBody(data))
            .isEqualTo(R.string.push_default_body)
    }
}
