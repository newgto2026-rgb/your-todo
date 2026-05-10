package com.neo.yourtodo.app.push

internal fun shouldOpenPushNotificationInApp(data: Map<String, String>): Boolean {
    if (data.isEmpty()) return false
    val type = data[PushNotificationContract.EXTRA_TYPE]?.takeIf { it.isNotBlank() }
    val deepLink = data[PushNotificationContract.EXTRA_DEEP_LINK]
        ?.takeIf { it.isNotBlank() }
    return type != null ||
        deepLink != null ||
        data[PushNotificationContract.EXTRA_NOTIFICATION_EVENT_ID]?.isNotBlank() == true
}

internal fun pushNotificationRequestCode(
    data: Map<String, String>,
    fallbackNonce: Long
): Int {
    val stableKey = data[PushNotificationContract.EXTRA_NOTIFICATION_EVENT_ID]
        ?.takeIf { it.isNotBlank() }
        ?: listOf(
            data[PushNotificationContract.EXTRA_TYPE],
            data[PushNotificationContract.EXTRA_DEEP_LINK],
            data[PushNotificationContract.EXTRA_BUNDLE_ID],
            data[PushNotificationContract.EXTRA_ASSIGNED_TODO_ID],
            data[PushNotificationContract.EXTRA_ACTOR_USER_ID],
            data[PushNotificationContract.EXTRA_ITEM_TITLE],
            fallbackNonce.toString()
        ).joinToString(separator = "|") { it.orEmpty() }
    return stableKey.hashCode()
}
