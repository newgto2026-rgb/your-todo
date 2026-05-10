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
        "FRIEND_REQUEST_RECEIVED",
        "ASSIGNMENT_BUNDLE_RECEIVED",
        "ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED",
        "ASSIGNMENT_BUNDLE_FULLY_DECIDED",
        "ASSIGNED_TODO_COMPLETED",
        "ASSIGNED_TODO_REOPENED",
        "ASSIGNED_TODO_CANCELED"
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
            "FRIEND_REQUEST_RECEIVED" -> R.string.push_friend_request_title
            "ASSIGNMENT_BUNDLE_RECEIVED" -> R.string.push_assignment_received_title
            "ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED",
            "ASSIGNMENT_BUNDLE_FULLY_DECIDED" -> R.string.push_assignment_decided_title
            "ASSIGNED_TODO_COMPLETED" -> R.string.push_assigned_todo_completed_title
            "ASSIGNED_TODO_REOPENED" -> R.string.push_assigned_todo_reopened_title
            "ASSIGNED_TODO_CANCELED" -> R.string.push_assigned_todo_canceled_title
            else -> R.string.push_default_title
        }

    @StringRes
    fun defaultBody(data: Map<String, String>): Int =
        when (data[PushNotificationContract.EXTRA_TYPE]) {
            "FRIEND_REQUEST_RECEIVED" -> R.string.push_friend_request_body
            "ASSIGNMENT_BUNDLE_RECEIVED" -> R.string.push_assignment_received_body
            "ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED",
            "ASSIGNMENT_BUNDLE_FULLY_DECIDED" -> R.string.push_assignment_decided_body
            "ASSIGNED_TODO_COMPLETED" -> R.string.push_assigned_todo_completed_body
            "ASSIGNED_TODO_REOPENED" -> R.string.push_assigned_todo_reopened_body
            "ASSIGNED_TODO_CANCELED" -> R.string.push_assigned_todo_canceled_body
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
            "ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED",
            "ASSIGNMENT_BUNDLE_FULLY_DECIDED" -> decisionBody(actionResult, itemTitle, itemCount)
            "ASSIGNED_TODO_COMPLETED" -> {
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
            "ASSIGNED_TODO_REOPENED" -> itemTitle?.let {
                PushNotificationText(R.string.push_assigned_todo_reopened_single_body, listOf(it))
            }
            "ASSIGNED_TODO_CANCELED" -> itemTitle?.let {
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
