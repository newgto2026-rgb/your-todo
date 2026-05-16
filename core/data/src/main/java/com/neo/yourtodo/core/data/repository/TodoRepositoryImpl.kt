package com.neo.yourtodo.core.data.repository

import android.util.Log
import com.neo.yourtodo.core.data.repository.todo.TodoCategoryStore
import com.neo.yourtodo.core.data.repository.todo.TodoFilterPreferences
import com.neo.yourtodo.core.data.repository.todo.TodoLocalTodoStore
import com.neo.yourtodo.core.data.repository.todo.TodoOutboxStore
import com.neo.yourtodo.core.data.repository.todo.TodoReminderReader
import com.neo.yourtodo.core.data.repository.todo.TodoSyncCoordinator
import com.neo.yourtodo.core.data.repository.todo.TodoSyncSessionProvider
import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.repository.TodoCategoryRepository
import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.repository.TodoReminderRepository
import com.neo.yourtodo.core.model.Category
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import com.neo.yourtodo.core.network.sync.TodoSyncNetworkDataSource
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class TodoRepositoryImpl @Inject constructor(
    todoDao: TodoDao,
    categoryDao: CategoryDao,
    todoOutboxDao: TodoOutboxDao,
    userPreferencesDataSource: UserPreferencesDataSource,
    todoSyncNetworkDataSource: TodoSyncNetworkDataSource,
    assignmentFeedFreshnessTracker: AssignmentFeedFreshnessTracker,
    authSessionRefresher: AuthSessionRefresher
) : TodoItemRepository, TodoCategoryRepository, TodoFilterRepository, TodoReminderRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val categoryStore = TodoCategoryStore(categoryDao, userPreferencesDataSource)
    private val syncSessionProvider = TodoSyncSessionProvider(userPreferencesDataSource)
    private val outboxStore = TodoOutboxStore(todoOutboxDao, json)
    private val todos = TodoLocalTodoStore(
        todoDao = todoDao,
        categoryStore = categoryStore,
        outboxStore = outboxStore,
        syncSessionProvider = syncSessionProvider
    )
    private val syncCoordinator = TodoSyncCoordinator(
        todoDao = todoDao,
        outboxStore = outboxStore,
        userPreferencesDataSource = userPreferencesDataSource,
        todoSyncNetworkDataSource = todoSyncNetworkDataSource,
        authSessionRefresher = authSessionRefresher,
        syncSessionProvider = syncSessionProvider,
        assignmentFeedFreshnessTracker = assignmentFeedFreshnessTracker,
        json = json
    )
    private val filterPreferences = TodoFilterPreferences(userPreferencesDataSource, categoryStore)
    private val reminderReader = TodoReminderReader(todoDao)

    override fun observeTodos(): Flow<List<TodoItem>> =
        todos.observeTodos()

    override fun observeTodosByDueDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TodoItem>> =
        todos.observeTodosByDueDateRange(startDate, endDate)

    override suspend fun getTodo(id: Long): TodoItem? =
        todos.getTodo(id)

    override suspend fun getTodosWithActiveReminder(): List<TodoItem> =
        reminderReader.getTodosWithActiveReminder()

    override suspend fun addTodo(
        title: String,
        dueDate: LocalDate?,
        categoryId: Long?,
        dueTimeMinutes: Int?,
        reminderAtEpochMillis: Long?,
        isReminderEnabled: Boolean,
        reminderRepeatType: ReminderRepeatType,
        reminderRepeatDaysMask: Int,
        reminderLeadMinutes: Int?,
        priority: TodoPriority
    ): Result<Long> = loggedResult("addTodo") {
        todos.addTodo(
            title = title,
            dueDate = dueDate,
            categoryId = categoryId,
            dueTimeMinutes = dueTimeMinutes,
            reminderAtEpochMillis = reminderAtEpochMillis,
            isReminderEnabled = isReminderEnabled,
            reminderRepeatType = reminderRepeatType,
            reminderRepeatDaysMask = reminderRepeatDaysMask,
            reminderLeadMinutes = reminderLeadMinutes,
            priority = priority
        )
    }

    override suspend fun updateTodo(
        id: Long,
        title: String,
        dueDate: LocalDate?,
        categoryId: Long?,
        dueTimeMinutes: Int?,
        reminderAtEpochMillis: Long?,
        isReminderEnabled: Boolean,
        reminderRepeatType: ReminderRepeatType,
        reminderRepeatDaysMask: Int,
        reminderLeadMinutes: Int?,
        priority: TodoPriority
    ): Result<Unit> = loggedResult("updateTodo") {
        todos.updateTodo(
            id = id,
            title = title,
            dueDate = dueDate,
            categoryId = categoryId,
            dueTimeMinutes = dueTimeMinutes,
            reminderAtEpochMillis = reminderAtEpochMillis,
            isReminderEnabled = isReminderEnabled,
            reminderRepeatType = reminderRepeatType,
            reminderRepeatDaysMask = reminderRepeatDaysMask,
            reminderLeadMinutes = reminderLeadMinutes,
            priority = priority
        )
    }

    override suspend fun deleteTodo(id: Long): Result<Unit> = loggedResult("deleteTodo") {
        todos.deleteTodo(id)
    }

    override suspend fun toggleTodoDone(id: Long): Result<Unit> = loggedResult("toggleTodoDone") {
        todos.toggleTodoDone(id)
    }

    override suspend fun syncTodos(): Result<Unit> = loggedResult("syncTodos") {
        syncCoordinator.syncTodos()
    }

    override fun observeSelectedFilter(): Flow<TodoFilter> =
        filterPreferences.observeSelectedFilter()

    override suspend fun setSelectedFilter(filter: TodoFilter): Result<Unit> =
        preferenceResult("setSelectedFilter") {
            filterPreferences.setSelectedFilter(filter)
        }

    override fun observeCategories(): Flow<List<Category>> =
        categoryStore.observeCategories()

    override suspend fun addCategory(name: String, colorHex: String?, icon: String?): Result<Long> =
        loggedResult("addCategory") {
            categoryStore.addCategory(name, colorHex, icon)
        }

    override suspend fun updateCategory(id: Long, name: String, colorHex: String?, icon: String?): Result<Unit> =
        loggedResult("updateCategory") {
            categoryStore.updateCategory(id, name, colorHex, icon)
        }

    override suspend fun deleteCategory(id: Long): Result<Unit> = loggedResult("deleteCategory") {
        categoryStore.deleteCategory(id)
    }

    override fun observeSelectedCategoryFilter(): Flow<Long?> =
        filterPreferences.observeSelectedCategoryFilter()

    override suspend fun setSelectedCategoryFilter(categoryId: Long?): Result<Unit> =
        preferenceResult("setSelectedCategoryFilter") {
            filterPreferences.setSelectedCategoryFilter(categoryId)
        }

    override fun observeSelectedPriorityFilter(): Flow<TodoPriorityFilter> =
        filterPreferences.observeSelectedPriorityFilter()

    override suspend fun setSelectedPriorityFilter(filter: TodoPriorityFilter): Result<Unit> =
        preferenceResult("setSelectedPriorityFilter") {
            filterPreferences.setSelectedPriorityFilter(filter)
        }

    override fun observeSelectedSortOption(): Flow<TodoSortOption> =
        filterPreferences.observeSelectedSortOption()

    override suspend fun setSelectedSortOption(option: TodoSortOption): Result<Unit> =
        preferenceResult("setSelectedSortOption") {
            filterPreferences.setSelectedSortOption(option)
        }

    private suspend fun <T> loggedResult(action: String, block: suspend () -> T): Result<T> =
        runCatching { block() }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            logError(action, throwable)
        }

    private suspend fun <T> preferenceResult(action: String, block: suspend () -> T): Result<T> =
        runCatching { block() }.onFailure { throwable ->
            logPreferenceFailure(action, throwable)
        }

    private fun logError(action: String, throwable: Throwable) {
        Log.e(TAG, "action=$action failure=${throwable.message}", throwable)
    }

    private fun logPreferenceFailure(action: String, throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        logError(action, throwable)
    }

    private companion object {
        private const val TAG = "TodoRepository"
    }
}
