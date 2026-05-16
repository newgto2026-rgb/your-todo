package com.neo.yourtodo.core.datastore.source

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object UserPreferenceKeys {
    val SCHEMA_VERSION = intPreferencesKey("user_preferences_schema_version")
    val AUTH_ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
    val AUTH_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
    val AUTH_ENCRYPTED_ACCESS_TOKEN = stringPreferencesKey("auth_access_token_encrypted")
    val AUTH_ENCRYPTED_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token_encrypted")
    val AUTH_USER_ID = stringPreferencesKey("auth_user_id")
    val AUTH_USER_NICKNAME = stringPreferencesKey("auth_user_nickname")
    val AUTH_USER_EMAIL = stringPreferencesKey("auth_user_email")
    val AUTH_ONBOARDING_REQUIRED = intPreferencesKey("auth_onboarding_required")
    val SELECTED_TODO_FILTER = stringPreferencesKey("selected_todo_filter")
    val SELECTED_TODO_CATEGORY_FILTER = longPreferencesKey("selected_todo_category_filter")
    val SELECTED_TODO_PRIORITY_FILTER = stringPreferencesKey("selected_todo_priority_filter")
    val SELECTED_TODO_SORT_OPTION = stringPreferencesKey("selected_todo_sort_option")
    val TODO_SYNC_CURSOR = stringPreferencesKey("todo_sync_cursor")
    val TODO_SYNC_HALT_REASON = stringPreferencesKey("todo_sync_halt_reason")
    val PUSH_CURRENT_TOKEN = stringPreferencesKey("push_current_token")
    val PUSH_REGISTERED_TOKEN = stringPreferencesKey("push_registered_token")
}
