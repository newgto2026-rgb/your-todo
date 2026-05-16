package com.neo.yourtodo.core.datastore.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ONBOARDING_REQUIRED
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_EMAIL
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_ID
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_NICKNAME
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.PUSH_CURRENT_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.PUSH_REGISTERED_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_CATEGORY_FILTER
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_FILTER
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_PRIORITY_FILTER
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_SORT_OPTION
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.TODO_SYNC_CURSOR
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.TODO_SYNC_HALT_REASON
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserPreferencesDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val authTokenStoragePolicy: AuthTokenStoragePolicy
) : UserPreferencesDataSource {

    override val authSession: Flow<AuthSessionData?> =
        dataStore.data
            .map { prefs -> AuthPreferencesSnapshot.from(prefs) }
            .distinctUntilChanged()
            .map { authPreferences -> authPreferences.toAuthSession(authTokenStoragePolicy) }

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

    override val selectedTodoSortOption: Flow<TodoSortOption> =
        dataStore.data.map { prefs ->
            val stored = prefs[SELECTED_TODO_SORT_OPTION]
            stored?.let { value -> TodoSortOption.entries.find { it.name == value } }
                ?: TodoSortOption.DEFAULT
        }

    override val todoSyncCursor: Flow<String?> =
        dataStore.data.map { prefs -> prefs[TODO_SYNC_CURSOR] }

    override val todoSyncHaltReason: Flow<String?> =
        dataStore.data.map { prefs -> prefs[TODO_SYNC_HALT_REASON] }

    override val pushCurrentToken: Flow<String?> =
        dataStore.data.map { prefs -> prefs[PUSH_CURRENT_TOKEN] }

    override val pushRegisteredToken: Flow<String?> =
        dataStore.data.map { prefs -> prefs[PUSH_REGISTERED_TOKEN] }

    override suspend fun saveAuthSession(session: AuthSessionData) {
        dataStore.edit { prefs ->
            authTokenStoragePolicy.saveTokens(
                preferences = prefs,
                tokens = AuthTokenPair(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken
                )
            )
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
            authTokenStoragePolicy.clearTokens(prefs)
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

    override suspend fun setSelectedTodoSortOption(option: TodoSortOption) {
        dataStore.edit { prefs ->
            prefs[SELECTED_TODO_SORT_OPTION] = option.name
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

    override suspend fun setPushCurrentToken(token: String?) {
        dataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(PUSH_CURRENT_TOKEN)
            } else {
                prefs[PUSH_CURRENT_TOKEN] = token
            }
        }
    }

    override suspend fun setPushRegisteredToken(token: String?) {
        dataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(PUSH_REGISTERED_TOKEN)
            } else {
                prefs[PUSH_REGISTERED_TOKEN] = token
            }
        }
    }

}

private data class AuthPreferencesSnapshot(
    val encryptedAccessToken: String?,
    val encryptedRefreshToken: String?,
    val legacyAccessToken: String?,
    val legacyRefreshToken: String?,
    val userId: String?,
    val nickname: String?,
    val email: String?,
    val onboardingRequired: Int?
) {
    fun toAuthSession(authTokenStoragePolicy: AuthTokenStoragePolicy): AuthSessionData? {
        val tokens = authTokenStoragePolicy.readTokens(
            encryptedAccessToken = encryptedAccessToken,
            encryptedRefreshToken = encryptedRefreshToken,
            legacyAccessToken = legacyAccessToken,
            legacyRefreshToken = legacyRefreshToken
        )

        return if (tokens == null || userId.isNullOrBlank() || email.isNullOrBlank()) {
            null
        } else {
            AuthSessionData(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                userId = userId,
                nickname = nickname,
                email = email,
                onboardingRequired = onboardingRequired == 1
            )
        }
    }

    companion object {
        fun from(preferences: Preferences): AuthPreferencesSnapshot =
            AuthPreferencesSnapshot(
                encryptedAccessToken = preferences[AUTH_ENCRYPTED_ACCESS_TOKEN],
                encryptedRefreshToken = preferences[AUTH_ENCRYPTED_REFRESH_TOKEN],
                legacyAccessToken = preferences[AUTH_ACCESS_TOKEN],
                legacyRefreshToken = preferences[AUTH_REFRESH_TOKEN],
                userId = preferences[AUTH_USER_ID],
                nickname = preferences[AUTH_USER_NICKNAME],
                email = preferences[AUTH_USER_EMAIL],
                onboardingRequired = preferences[AUTH_ONBOARDING_REQUIRED]
            )
    }
}
