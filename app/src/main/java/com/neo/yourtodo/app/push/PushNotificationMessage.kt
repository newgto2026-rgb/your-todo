package com.neo.yourtodo.app.push

import androidx.annotation.StringRes
import com.neo.yourtodo.R

object PushNotificationMessage {
    @StringRes
    fun defaultTitle(data: Map<String, String>): Int =
        when (data[PushNotificationContract.EXTRA_TYPE]) {
            "FRIEND_REQUEST_RECEIVED" -> R.string.push_friend_request_title
            "ASSIGNMENT_BUNDLE_RECEIVED" -> R.string.push_assignment_received_title
            "ASSIGNMENT_BUNDLE_PARTIALLY_DECIDED",
            "ASSIGNMENT_BUNDLE_FULLY_DECIDED" -> R.string.push_assignment_decided_title
            "ASSIGNED_TODO_COMPLETED" -> R.string.push_assigned_todo_completed_title
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
            "ASSIGNED_TODO_CANCELED" -> R.string.push_assigned_todo_canceled_body
            else -> R.string.push_default_body
        }
}
