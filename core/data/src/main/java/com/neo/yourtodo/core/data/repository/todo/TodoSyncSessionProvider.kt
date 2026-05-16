package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
internal class TodoSyncSessionProvider @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource
) {
    suspend fun currentSessionForSync(): AuthSessionData? =
        userPreferencesDataSource.authSession.first()
            ?.takeUnless { it.onboardingRequired }
}
