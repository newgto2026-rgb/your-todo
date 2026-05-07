package com.example.myfirstapp.core.datastore.source

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object UserPreferenceKeys {
    val SCHEMA_VERSION = intPreferencesKey("user_preferences_schema_version")
    val SELECTED_TODO_FILTER = stringPreferencesKey("selected_todo_filter")
    val SELECTED_TODO_CATEGORY_FILTER = longPreferencesKey("selected_todo_category_filter")
    val SELECTED_TODO_PRIORITY_FILTER = stringPreferencesKey("selected_todo_priority_filter")
}
