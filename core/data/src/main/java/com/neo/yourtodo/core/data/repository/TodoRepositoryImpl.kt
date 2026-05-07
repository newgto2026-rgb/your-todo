package com.neo.yourtodo.core.data.repository

import android.util.Log
import com.neo.yourtodo.core.data.mapper.toDomain
import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.repository.TodoCategoryRepository
import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.repository.TodoReminderRepository
import com.neo.yourtodo.core.model.Category
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoCategoryFilter
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

class TodoRepositoryImpl @Inject constructor(
    private val todoDao: TodoDao,
    private val categoryDao: CategoryDao,
    private val userPreferencesDataSource: UserPreferencesDataSource
) : TodoItemRepository, TodoCategoryRepository, TodoFilterRepository, TodoReminderRepository {

    override fun observeTodos(): Flow<List<TodoItem>> =
        todoDao.observeTodos().map { entities -> entities.map { it.toDomain() } }

    override fun observeTodosByDueDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TodoItem>> =
        todoDao.observeTodosByDueDateRange(
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay()
        ).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTodo(id: Long): TodoItem? = todoDao.getTodoById(id)?.toDomain()

    override suspend fun getTodosWithActiveReminder(): List<TodoItem> =
        todoDao.getTodosWithActiveReminder().map { it.toDomain() }

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
    ): Result<Long> = runCatching {
        validateCategoryId(categoryId)
        val now = System.currentTimeMillis()
        todoDao.insert(
            TodoEntity(
                title = title,
                isDone = false,
                dueDateEpochDay = dueDate?.toEpochDay(),
                dueTimeMinutes = dueTimeMinutes,
                reminderAtEpochMillis = reminderAtEpochMillis,
                isReminderEnabled = isReminderEnabled && reminderAtEpochMillis != null,
                reminderRepeatType = reminderRepeatType.name,
                reminderRepeatDaysMask = reminderRepeatDaysMask,
                reminderLeadMinutes = reminderLeadMinutes,
                createdAt = now,
                updatedAt = now,
                categoryId = categoryId,
                priority = priority.name
            )
        )
    }.onFailure { throwable ->
        logError("addTodo", throwable)
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
    ): Result<Unit> = runCatching {
        validateCategoryId(categoryId)
        val existing = todoDao.getTodoById(id) ?: throw IllegalStateException("Todo not found")
        todoDao.update(
            existing.copy(
                title = title,
                dueDateEpochDay = dueDate?.toEpochDay(),
                dueTimeMinutes = dueTimeMinutes,
                reminderAtEpochMillis = reminderAtEpochMillis,
                isReminderEnabled = isReminderEnabled && reminderAtEpochMillis != null,
                reminderRepeatType = reminderRepeatType.name,
                reminderRepeatDaysMask = reminderRepeatDaysMask,
                reminderLeadMinutes = reminderLeadMinutes,
                updatedAt = System.currentTimeMillis(),
                categoryId = categoryId,
                priority = priority.name
            )
        )
    }.onFailure { throwable ->
        logError("updateTodo", throwable)
    }

    override suspend fun deleteTodo(id: Long): Result<Unit> = runCatching {
        val existing = todoDao.getTodoById(id) ?: throw IllegalStateException("Todo not found")
        todoDao.delete(existing)
    }.onFailure { throwable ->
        logError("deleteTodo", throwable)
    }

    override suspend fun toggleTodoDone(id: Long): Result<Unit> = runCatching {
        val existing = todoDao.getTodoById(id) ?: throw IllegalStateException("Todo not found")
        todoDao.update(
            existing.copy(
                isDone = !existing.isDone,
                isReminderEnabled = if (!existing.isDone) false else existing.isReminderEnabled,
                reminderAtEpochMillis = if (!existing.isDone) null else existing.reminderAtEpochMillis,
                reminderRepeatType = if (!existing.isDone) ReminderRepeatType.NONE.name else existing.reminderRepeatType,
                reminderRepeatDaysMask = if (!existing.isDone) 0 else existing.reminderRepeatDaysMask,
                updatedAt = System.currentTimeMillis()
            )
        )
    }.onFailure { throwable ->
        logError("toggleTodoDone", throwable)
    }

    override fun observeSelectedFilter(): Flow<TodoFilter> = userPreferencesDataSource.selectedTodoFilter

    override suspend fun setSelectedFilter(filter: TodoFilter): Result<Unit> = runCatching {
        userPreferencesDataSource.setSelectedTodoFilter(filter)
    }.onFailure { throwable ->
        logError("setSelectedFilter", throwable)
    }

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeCategories().map { categories -> categories.map { it.toDomain() } }

    override suspend fun addCategory(name: String, colorHex: String?, icon: String?): Result<Long> = runCatching {
        ensureUniqueCategoryName(name, excludeCategoryId = null)
        val now = System.currentTimeMillis()
        categoryDao.insert(
            CategoryEntity(
                name = name,
                colorHex = colorHex,
                icon = icon,
                createdAt = now,
                updatedAt = now
            )
        )
    }.onFailure { throwable ->
        logError("addCategory", throwable)
    }

    override suspend fun updateCategory(id: Long, name: String, colorHex: String?, icon: String?): Result<Unit> = runCatching {
        val existing = categoryDao.getCategoryById(id) ?: throw IllegalStateException("Category not found")
        ensureUniqueCategoryName(name, excludeCategoryId = id)
        categoryDao.update(
            existing.copy(
                name = name,
                colorHex = colorHex,
                icon = icon,
                updatedAt = System.currentTimeMillis()
            )
        )
    }.onFailure { throwable ->
        logError("updateCategory", throwable)
    }

    override suspend fun deleteCategory(id: Long): Result<Unit> = runCatching {
        val existing = categoryDao.getCategoryById(id) ?: throw IllegalStateException("Category not found")
        categoryDao.delete(existing)

        // 선택된 카테고리가 삭제되면 전체(All)로 fallback
        if (userPreferencesDataSource.selectedTodoCategoryFilter.first() == id) {
            userPreferencesDataSource.setSelectedTodoCategoryFilter(null)
        }
    }.onFailure { throwable ->
        logError("deleteCategory", throwable)
    }

    override fun observeSelectedCategoryFilter(): Flow<Long?> =
        userPreferencesDataSource.selectedTodoCategoryFilter

    override suspend fun setSelectedCategoryFilter(categoryId: Long?): Result<Unit> = runCatching {
        val isUncategorized = categoryId == TodoCategoryFilter.UNCATEGORIZED_FILTER_ID
        if (categoryId != null && !isUncategorized && categoryDao.getCategoryById(categoryId) == null) {
            throw IllegalArgumentException("Category not found")
        }
        userPreferencesDataSource.setSelectedTodoCategoryFilter(categoryId)
    }.onFailure { throwable ->
        logError("setSelectedCategoryFilter", throwable)
    }

    override fun observeSelectedPriorityFilter(): Flow<TodoPriorityFilter> =
        userPreferencesDataSource.selectedTodoPriorityFilter

    override suspend fun setSelectedPriorityFilter(filter: TodoPriorityFilter): Result<Unit> = runCatching {
        userPreferencesDataSource.setSelectedTodoPriorityFilter(filter)
    }.onFailure { throwable ->
        logError("setSelectedPriorityFilter", throwable)
    }

    private suspend fun validateCategoryId(categoryId: Long?) {
        if (categoryId != null && categoryDao.getCategoryById(categoryId) == null) {
            throw IllegalArgumentException("Category not found")
        }
    }

    private suspend fun ensureUniqueCategoryName(name: String, excludeCategoryId: Long?) {
        val duplicate = categoryDao.getCategoryByName(name)
        if (duplicate != null && duplicate.id != excludeCategoryId) {
            throw IllegalArgumentException("Category name already exists")
        }

        // room의 NOCASE index가 locale 독립적이지 않을 수 있어 repo 레벨에서 한 번 더 정규화 검증
        val normalized = name.lowercase(Locale.ROOT)
        if (duplicate != null && duplicate.id != excludeCategoryId && duplicate.name.lowercase(Locale.ROOT) == normalized) {
            throw IllegalArgumentException("Category name already exists")
        }
    }

    private fun logError(action: String, throwable: Throwable) {
        Log.e(TAG, "action=$action failure=${throwable.message}", throwable)
    }

    private companion object {
        private const val TAG = "TodoRepository"
    }
}
