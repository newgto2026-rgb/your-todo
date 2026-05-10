package com.neo.yourtodo.app.push

private const val AssignmentBundleReceivedType = "ASSIGNMENT_BUNDLE_RECEIVED"
private const val ReceivedAssignmentDeepLinkPrefix = "yourtodo://assignment-bundles/received/"

internal fun shouldOpenPushNotificationInApp(data: Map<String, String>): Boolean {
    val type = data[PushNotificationContract.EXTRA_TYPE]
    val deepLink = data[PushNotificationContract.EXTRA_DEEP_LINK]
    return type == AssignmentBundleReceivedType ||
        deepLink?.startsWith(ReceivedAssignmentDeepLinkPrefix) == true
}
