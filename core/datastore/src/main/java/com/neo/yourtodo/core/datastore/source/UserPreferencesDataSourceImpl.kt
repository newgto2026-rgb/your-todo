package com.neo.yourtodo.core.datastore.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ONBOARDING_REQUIRED
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_EMAIL
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_ID
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_NICKNAME
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_CATEGORY_FILTER
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_FILTER
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_PRIORITY_FILTER
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.TODO_SYNC_CURSOR
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.TODO_SYNC_HALT_REASON
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserPreferencesDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesDataSource {

    override val authSession: Flow<AuthSessionData?> =
        dataStore.data.map { prefs ->
            val accessToken = prefs[AUTH_ACCESS_TOKEN]
            val refreshToken = prefs[AUTH_REFRESH_TOKEN]
            val userId = prefs[AUTH_USER_ID]
            val email = prefs[AUTH_USER_EMAIL]
            if (
                accessToken.isNullOrBlank() ||
                refreshToken.isNullOrBlank() ||
                userId.isNullOrBlank() ||
                email.isNullOrBlank()
            ) {
                null
            } else {
                AuthSessionData(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userId = userId,
                    nickname = prefs[AUTH_USER_NICKNAME],
                    email = email,
                    onboardingRequired = prefs[AUTH_ONBOARDING_REQUIRED] == 1
                )
            }
        }

    override val selectedTodoFilter: Flow<TodoFilter> =
        dataStore.data.map { prefs ->
            val stored = prefs[SELECTED_TODO_FILTER]
            stored?.let { value -> TodoFilter.entries.find { it.name == value } } ?: TodoFilter.ALL
        }

    override val selectedTodoCategoryFilter: Flow<Long?> =
        dataStore.data.map { prefs -> prefs[SELECTED_TODO_CATEGORY_FILTER] }

    override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> =
        dataStore.data.map { prefs ->
            val stored = prefs[SELECTED_TODO_PRIORITY_FILTER]
            stored?.let { value -> TodoPriorityFilter.entries.find { it.name == value } }
                ?: TodoPriorityFilter.ALL
        }

    override val todoSyncCursor: Flow<String?> =
        dataStore.data.map { prefs -> prefs[TODO_SYNC_CURSOR] }

    override val todoSyncHaltReason: Flow<String?> =
        dataStore.data.map { prefs -> prefs[TODO_SYNC_HALT_REASON] }

    override suspend fun saveAuthSession(session: AuthSessionData) {
        dataStore.edit { prefs ->
            prefs[AUTH_ACCESS_TOKEN] = session.accessToken
            prefs[AUTH_REFRESH_TOKEN] = session.refreshToken
            prefs[AUTH_USER_ID] = session.userId
            if (session.nickname.isNullOrBlank()) {
                prefs.remove(AUTH_USER_NICKNAME)
            } else {
                prefs[AUTH_USER_NICKNAME] = session.nickname
            }
            prefs[AUTH_USER_EMAIL] = session.email
            prefs[AUTH_ONBOARDING_REQUIRED] = if (session.onboardingRequired) 1 else 0
        }
    }

    override suspend fun clearAuthSession() {
        dataStore.edit { prefs ->
            prefs.remove(AUTH_ACCESS_TOKEN)
            prefs.remove(AUTH_REFRESH_TOKEN)
            prefs.remove(AUTH_USER_ID)
            prefs.remove(AUTH_USER_NICKNAME)
            prefs.remove(AUTH_USER_EMAIL)
            prefs.remove(AUTH_ONBOARDING_REQUIRED)
        }
    }

    override suspend fun setSelectedTodoFilter(filter: TodoFilter) {
        dataStore.edit { prefs ->
            prefs[SELECTED_TODO_FILTER] = filter.name
        }
    }

    override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) {
        dataStore.edit { prefs ->
            if (categoryId == null) {
                prefs.remove(SELECTED_TODO_CATEGORY_FILTER)
            } else {
                prefs[SELECTED_TODO_CATEGORY_FILTER] = categoryId
            }
        }
    }

    override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) {
        dataStore.edit { prefs ->
            prefs[SELECTED_TODO_PRIORITY_FILTER] = filter.name
        }
    }

    override suspend fun setTodoSyncCursor(cursor: String?) {
        dataStore.edit { prefs ->
            if (cursor.isNullOrBlank()) {
                prefs.remove(TODO_SYNC_CURSOR)
            } else {
                prefs[TODO_SYNC_CURSOR] = cursor
            }
        }
    }

    override suspend fun setTodoSyncHaltReason(reason: String?) {
        dataStore.edit { prefs ->
            if (reason.isNullOrBlank()) {
                prefs.remove(TODO_SYNC_HALT_REASON)
            } else {
                prefs[TODO_SYNC_HALT_REASON] = reason
            }
        }
    }

    override suspend fun clearTodoSyncState() {
        dataStore.edit { prefs ->
            prefs.remove(TODO_SYNC_CURSOR)
            prefs.remove(TODO_SYNC_HALT_REASON)
        }
    }

}
