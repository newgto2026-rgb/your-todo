package com.neo.yourtodo.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.domain.usecase.RegisterPushTokenUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class YourTodoFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var registerPushToken: RegisterPushTokenUseCase

    @Inject
    lateinit var refreshWorkspace: RefreshWorkspaceUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNewToken(token: String) {
        serviceScope.launch {
            registerPushToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        serviceScope.launch {
            refreshWorkspace()
        }
        val fallbackTitle = PushNotificationMessage.title(message.data)
        val fallbackBody = PushNotificationMessage.body(message.data)
        val title = message.notification?.title
            ?: resolvePushText(fallbackTitle)
        val body = message.notification?.body
            ?: resolvePushText(fallbackBody)
        PushNotificationHelper.show(
            context = this,
            title = title,
            body = body,
            data = message.data
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun resolvePushText(text: PushNotificationText): String =
        text.pluralsResId?.let { pluralsResId ->
            resources.getQuantityString(pluralsResId, text.quantity, *text.args.toTypedArray())
        } ?: getString(text.resId, *text.args.toTypedArray())
}
