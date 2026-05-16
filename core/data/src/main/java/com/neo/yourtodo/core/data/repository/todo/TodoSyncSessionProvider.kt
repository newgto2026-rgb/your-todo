package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import kotlinx.coroutines.flow.first

internal class TodoSyncSessionProvider(
    private val userPreferencesDataSource: UserPreferencesDataSource
) {
    suspend fun currentSessionForSync(): AuthSessionData? =
        userPreferencesDataSource.authSession.first()
            ?.takeUnless { it.onboardingRequired }
}
