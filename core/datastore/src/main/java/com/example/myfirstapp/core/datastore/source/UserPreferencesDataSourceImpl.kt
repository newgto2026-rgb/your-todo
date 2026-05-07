package com.example.myfirstapp.core.datastore.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.myfirstapp.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_CATEGORY_FILTER
import com.example.myfirstapp.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_FILTER
import com.example.myfirstapp.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_PRIORITY_FILTER
import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoPriorityFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserPreferencesDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesDataSource {

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

}
