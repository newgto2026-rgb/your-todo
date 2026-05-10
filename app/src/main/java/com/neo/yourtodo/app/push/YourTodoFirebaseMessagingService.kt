package com.neo.yourtodo.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.domain.usecase.RegisterPushTokenUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class YourTodoFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var registerPushToken: RegisterPushTokenUseCase

    @Inject
    lateinit var refreshWorkspace: RefreshWorkspaceUseCase

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var userPreferencesDataSource: UserPreferencesDataSource

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNewToken(token: String) {
        serviceScope.launch {
            registerPushToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        serviceScope.launch {
            runCatching { refreshWorkspace() }
            showNotification(message)
        }
    }

    private suspend fun showNotification(message: RemoteMessage) {
        val data = message.data.withLocalItemTitle()
        val shouldUseLocalMessage = PushNotificationMessage.supportsLocalFormatting(data)
        val fallbackTitle = PushNotificationMessage.title(data)
        val fallbackBody = PushNotificationMessage.body(data)
        val title = if (shouldUseLocalMessage) {
            resolvePushText(fallbackTitle)
        } else {
            message.notification?.title ?: resolvePushText(fallbackTitle)
        }
        val body = if (shouldUseLocalMessage) {
            resolvePushText(fallbackBody)
        } else {
            message.notification?.body ?: resolvePushText(fallbackBody)
        }
        PushNotificationHelper.show(
            context = this,
            title = title,
            body = body,
            data = data
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

    private suspend fun Map<String, String>.withLocalItemTitle(): Map<String, String> {
        if (!this[PushNotificationContract.EXTRA_ITEM_TITLE].isNullOrBlank()) return this
        if (!needsAssignedTodoTitle()) return this
        val assignedTodoId = this[PushNotificationContract.EXTRA_ASSIGNED_TODO_ID]
            ?.takeIf { it.isNotBlank() }
            ?: return this
        val userId = userPreferencesDataSource.authSession.first()?.userId ?: return this
        val title = appDatabase.assignedTodoDao()
            .getAssignedTodoById(userId, assignedTodoId)
            ?.title
            ?.takeIf { it.isNotBlank() }
            ?: return this
        return this + (PushNotificationContract.EXTRA_ITEM_TITLE to title)
    }

    private fun Map<String, String>.needsAssignedTodoTitle(): Boolean =
        when (this[PushNotificationContract.EXTRA_TYPE]) {
            "ASSIGNED_TODO_COMPLETED",
            "ASSIGNED_TODO_REOPENED",
            "ASSIGNED_TODO_CANCELED" -> true
            else -> false
        }
}
