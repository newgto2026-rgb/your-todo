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
    fun directAssignmentReceivedPayloadUsesDirectCopy() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_RECEIVED,
            PushNotificationContract.EXTRA_ITEM_TITLE to "운동하기",
            PushNotificationContract.EXTRA_ACTOR_NICKNAME to "monday"
        )

        assertThat(PushNotificationMessage.defaultTitle(data))
            .isEqualTo(R.string.push_direct_assignment_received_title)
        val body = PushNotificationMessage.body(data)
        assertThat(body.resId).isEqualTo(R.string.push_direct_assignment_received_by_friend_body)
        assertThat(body.args).containsExactly("monday", "운동하기").inOrder()
    }

    @Test
    fun directAssignmentConsentPayloadsUsePermissionCopy() {
        val accepted = mapOf(
            PushNotificationContract.EXTRA_TYPE to PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_ACCEPTED,
            PushNotificationContract.EXTRA_ACTOR_NICKNAME to "monday"
        )
        val revoked = mapOf(
            PushNotificationContract.EXTRA_TYPE to PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_REVOKED,
            PushNotificationContract.EXTRA_ACTOR_NICKNAME to "monday"
        )

        assertThat(PushNotificationMessage.defaultTitle(accepted))
            .isEqualTo(R.string.push_direct_assignment_consent_accepted_title)
        assertThat(PushNotificationMessage.body(accepted).resId)
            .isEqualTo(R.string.push_direct_assignment_consent_accepted_by_friend_body)
        assertThat(PushNotificationMessage.defaultTitle(revoked))
            .isEqualTo(R.string.push_direct_assignment_consent_revoked_title)
        assertThat(PushNotificationMessage.body(revoked).resId)
            .isEqualTo(R.string.push_direct_assignment_consent_revoked_by_friend_body)
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
    fun defaultMessage_mapsFakeReopenedPayload() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "ASSIGNED_TODO_REOPENED",
            PushNotificationContract.EXTRA_DEEP_LINK to "yourtodo://assigned-todos/sent/assigned-id",
            PushNotificationContract.EXTRA_ASSIGNED_TODO_ID to "assigned-id"
        )

        assertThat(PushNotificationMessage.defaultTitle(data))
            .isEqualTo(R.string.push_assigned_todo_reopened_title)
        assertThat(PushNotificationMessage.defaultBody(data))
            .isEqualTo(R.string.push_assigned_todo_reopened_body)
    }

    @Test
    fun body_usesSingleDecisionPayloadTitle() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "ASSIGNMENT_BUNDLE_FULLY_DECIDED",
            PushNotificationContract.EXTRA_ACTION_RESULT to "ACCEPTED",
            PushNotificationContract.EXTRA_ITEM_COUNT to "1",
            PushNotificationContract.EXTRA_ITEM_TITLE to "분리수거"
        )

        val body = PushNotificationMessage.body(data)

        assertThat(body.resId).isEqualTo(R.string.push_assignment_accepted_single_body)
        assertThat(body.args).containsExactly("분리수거")
    }

    @Test
    fun body_usesMultiDecisionPayloadCountAlias() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED",
            PushNotificationContract.EXTRA_ACTION_RESULT to "REJECTED",
            PushNotificationContract.EXTRA_COUNT to "4"
        )

        val body = PushNotificationMessage.body(data)

        assertThat(body.pluralsResId).isEqualTo(R.plurals.push_assignment_rejected_multi_body)
        assertThat(body.quantity).isEqualTo(4)
        assertThat(body.args).containsExactly(4)
    }

    @Test
    fun body_usesStatusPayloadTitle() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "ASSIGNED_TODO_REOPENED",
            PushNotificationContract.EXTRA_ACTION_RESULT to "REOPENED",
            PushNotificationContract.EXTRA_ITEM_TITLE to "분리수거"
        )

        val body = PushNotificationMessage.body(data)

        assertThat(body.resId).isEqualTo(R.string.push_assigned_todo_reopened_single_body)
        assertThat(body.args).containsExactly("분리수거")
    }

    @Test
    fun body_usesCompletedPayloadTitle() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "ASSIGNED_TODO_COMPLETED",
            PushNotificationContract.EXTRA_ITEM_TITLE to "아침 운동"
        )

        val body = PushNotificationMessage.body(data)

        assertThat(body.resId).isEqualTo(R.string.push_assigned_todo_completed_single_body)
        assertThat(body.args).containsExactly("아침 운동")
    }

    @Test
    fun body_usesCompletedPayloadActorAndTitle() {
        val data = mapOf(
            PushNotificationContract.EXTRA_TYPE to "ASSIGNED_TODO_COMPLETED",
            PushNotificationContract.EXTRA_ACTOR_NICKNAME to "tee",
            PushNotificationContract.EXTRA_ITEM_TITLE to "아침 운동"
        )

        val body = PushNotificationMessage.body(data)

        assertThat(body.resId).isEqualTo(R.string.push_assigned_todo_completed_by_friend_body)
        assertThat(body.args).containsExactly("tee", "아침 운동").inOrder()
    }

    @Test
    fun defaultMessage_unknownPayloadFallsBackToGenericUpdate() {
        val data = mapOf(PushNotificationContract.EXTRA_TYPE to "UNKNOWN")

        assertThat(PushNotificationMessage.defaultTitle(data))
            .isEqualTo(R.string.push_default_title)
        assertThat(PushNotificationMessage.defaultBody(data))
            .isEqualTo(R.string.push_default_body)
    }

    @Test
    fun supportsLocalFormatting_returnsFalseForUnmappedPushType() {
        val data = mapOf(PushNotificationContract.EXTRA_TYPE to "TODO_REMINDER")

        assertThat(PushNotificationMessage.supportsLocalFormatting(data)).isFalse()
    }

    @Test
    fun supportsLocalFormatting_returnsTrueForMappedPushType() {
        val data = mapOf(PushNotificationContract.EXTRA_TYPE to "ASSIGNED_TODO_COMPLETED")

        assertThat(PushNotificationMessage.supportsLocalFormatting(data)).isTrue()
    }
}
