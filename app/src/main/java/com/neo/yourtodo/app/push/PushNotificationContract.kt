package com.neo.yourtodo.app.push

object PushNotificationContract {
    const val ACTION_OPEN_PUSH_NOTIFICATION = "com.neo.yourtodo.action.OPEN_PUSH_NOTIFICATION"
    const val EXTRA_TYPE = "type"
    const val EXTRA_DEEP_LINK = "deepLink"
    const val EXTRA_NOTIFICATION_EVENT_ID = "notificationEventId"
    const val EXTRA_BUNDLE_ID = "bundleId"
    const val EXTRA_ASSIGNED_TODO_ID = "assignedTodoId"
    const val EXTRA_ACTOR_USER_ID = "actorUserId"
    const val EXTRA_ACTOR_NICKNAME = "actorNickname"

    const val CHANNEL_ID = "friend_updates"
}
