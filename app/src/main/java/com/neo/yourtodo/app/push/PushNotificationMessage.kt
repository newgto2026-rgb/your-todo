package com.neo.yourtodo.app.push

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.neo.yourtodo.R

data class PushNotificationText(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
    @PluralsRes val pluralsResId: Int? = null,
    val quantity: Int = 0
)

object PushNotificationMessage {
    private val locallyFormattedTypes = setOf(
        PushNotificationContract.TYPE_FRIEND_REQUEST_RECEIVED,
        PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_RECEIVED,
        PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED,
        PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_FULLY_DECIDED,
        PushNotificationContract.TYPE_ASSIGNED_TODO_COMPLETED,
        PushNotificationContract.TYPE_ASSIGNED_TODO_REOPENED,
        PushNotificationContract.TYPE_ASSIGNED_TODO_CANCELED,
        PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_RECEIVED,
        PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_ACCEPTED,
        PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_REVOKED
    )

    fun supportsLocalFormatting(data: Map<String, String>): Boolean =
        data[PushNotificationContract.EXTRA_TYPE] in locallyFormattedTypes

    fun title(data: Map<String, String>): PushNotificationText =
        PushNotificationText(defaultTitle(data))

    fun body(data: Map<String, String>): PushNotificationText =
        customBody(data) ?: PushNotificationText(defaultBody(data))

    @StringRes
    fun defaultTitle(data: Map<String, String>): Int =
        when (data[PushNotificationContract.EXTRA_TYPE]) {
            PushNotificationContract.TYPE_FRIEND_REQUEST_RECEIVED -> R.string.push_friend_request_title
            PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_RECEIVED -> R.string.push_assignment_received_title
            PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_RECEIVED -> R.string.push_direct_assignment_received_title
            PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_ACCEPTED ->
                R.string.push_direct_assignment_consent_accepted_title
            PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_REVOKED ->
                R.string.push_direct_assignment_consent_revoked_title
            PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED,
            PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_FULLY_DECIDED -> R.string.push_assignment_decided_title
            PushNotificationContract.TYPE_ASSIGNED_TODO_COMPLETED -> R.string.push_assigned_todo_completed_title
            PushNotificationContract.TYPE_ASSIGNED_TODO_REOPENED -> R.string.push_assigned_todo_reopened_title
            PushNotificationContract.TYPE_ASSIGNED_TODO_CANCELED -> R.string.push_assigned_todo_canceled_title
            else -> R.string.push_default_title
        }

    @StringRes
    fun defaultBody(data: Map<String, String>): Int =
        when (data[PushNotificationContract.EXTRA_TYPE]) {
            PushNotificationContract.TYPE_FRIEND_REQUEST_RECEIVED -> R.string.push_friend_request_body
            PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_RECEIVED -> R.string.push_assignment_received_body
            PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_RECEIVED -> R.string.push_direct_assignment_received_body
            PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_ACCEPTED ->
                R.string.push_direct_assignment_consent_accepted_body
            PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_REVOKED ->
                R.string.push_direct_assignment_consent_revoked_body
            PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED,
            PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_FULLY_DECIDED -> R.string.push_assignment_decided_body
            PushNotificationContract.TYPE_ASSIGNED_TODO_COMPLETED -> R.string.push_assigned_todo_completed_body
            PushNotificationContract.TYPE_ASSIGNED_TODO_REOPENED -> R.string.push_assigned_todo_reopened_body
            PushNotificationContract.TYPE_ASSIGNED_TODO_CANCELED -> R.string.push_assigned_todo_canceled_body
            else -> R.string.push_default_body
        }

    private fun customBody(data: Map<String, String>): PushNotificationText? {
        val type = data[PushNotificationContract.EXTRA_TYPE]
        val actionResult = data[PushNotificationContract.EXTRA_ACTION_RESULT]
        val itemTitle = data[PushNotificationContract.EXTRA_ITEM_TITLE]
            ?.takeIf { it.isNotBlank() }
        val actorNickname = data[PushNotificationContract.EXTRA_ACTOR_NICKNAME]
            ?.takeIf { it.isNotBlank() }
        val itemCount = data[PushNotificationContract.EXTRA_ITEM_COUNT]?.toIntOrNull()
            ?: data[PushNotificationContract.EXTRA_COUNT]?.toIntOrNull()

        return when (type) {
            PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_RECEIVED -> {
                if (itemTitle != null && actorNickname != null) {
                    PushNotificationText(
                        R.string.push_direct_assignment_received_by_friend_body,
                        listOf(actorNickname, itemTitle)
                    )
                } else {
                    itemTitle?.let {
                        PushNotificationText(R.string.push_direct_assignment_received_single_body, listOf(it))
                    }
                }
            }
            PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_ACCEPTED -> actorNickname?.let {
                PushNotificationText(R.string.push_direct_assignment_consent_accepted_by_friend_body, listOf(it))
            }
            PushNotificationContract.TYPE_DIRECT_ASSIGNMENT_CONSENT_REVOKED -> actorNickname?.let {
                PushNotificationText(R.string.push_direct_assignment_consent_revoked_by_friend_body, listOf(it))
            }
            PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED,
            PushNotificationContract.TYPE_ASSIGNMENT_BUNDLE_FULLY_DECIDED -> decisionBody(actionResult, itemTitle, itemCount)
            PushNotificationContract.TYPE_ASSIGNED_TODO_COMPLETED -> {
                if (itemTitle != null && actorNickname != null) {
                    PushNotificationText(
                        R.string.push_assigned_todo_completed_by_friend_body,
                        listOf(actorNickname, itemTitle)
                    )
                } else {
                    itemTitle?.let {
                        PushNotificationText(R.string.push_assigned_todo_completed_single_body, listOf(it))
                    }
                }
            }
            PushNotificationContract.TYPE_ASSIGNED_TODO_REOPENED -> itemTitle?.let {
                PushNotificationText(R.string.push_assigned_todo_reopened_single_body, listOf(it))
            }
            PushNotificationContract.TYPE_ASSIGNED_TODO_CANCELED -> itemTitle?.let {
                PushNotificationText(R.string.push_assigned_todo_canceled_single_body, listOf(it))
            }
            else -> null
        }
    }

    private fun decisionBody(
        actionResult: String?,
        itemTitle: String?,
        itemCount: Int?
    ): PushNotificationText? =
        when (actionResult) {
            "ACCEPTED" -> {
                if (itemCount == 1 && itemTitle != null) {
                    PushNotificationText(R.string.push_assignment_accepted_single_body, listOf(itemTitle))
                } else if (itemCount != null && itemCount > 1) {
                    PushNotificationText(
                        resId = R.string.push_assignment_decided_body,
                        args = listOf(itemCount),
                        pluralsResId = R.plurals.push_assignment_accepted_multi_body,
                        quantity = itemCount
                    )
                } else {
                    null
                }
            }
            "REJECTED" -> {
                if (itemCount == 1 && itemTitle != null) {
                    PushNotificationText(R.string.push_assignment_rejected_single_body, listOf(itemTitle))
                } else if (itemCount != null && itemCount > 1) {
                    PushNotificationText(
                        resId = R.string.push_assignment_decided_body,
                        args = listOf(itemCount),
                        pluralsResId = R.plurals.push_assignment_rejected_multi_body,
                        quantity = itemCount
                    )
                } else {
                    null
                }
            }
            "MIXED" -> {
                if (itemCount != null && itemCount > 0) {
                    PushNotificationText(
                        resId = R.string.push_assignment_decided_body,
                        args = listOf(itemCount),
                        pluralsResId = R.plurals.push_assignment_mixed_multi_body,
                        quantity = itemCount
                    )
                } else {
                    null
                }
            }
            else -> null
        }
}
