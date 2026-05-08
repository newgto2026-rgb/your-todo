package com.neo.yourtodo.core.testing.repository

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Locale

class FakeTodoRepository :
    TodoItemRepository,
    TodoCategoryRepository,
    TodoFilterRepository,
    TodoReminderRepository {
    private val todos = MutableStateFlow<List<TodoItem>>(emptyList())
    private val categories = MutableStateFlow<List<Category>>(emptyList())
    private val selectedFilter = MutableStateFlow(TodoFilter.ALL)
    private val selectedCategoryFilter = MutableStateFlow<Long?>(null)
    private val selectedPriorityFilter = MutableStateFlow(TodoPriorityFilter.ALL)
    private var idSeed = 1L
    private var categoryIdSeed = 1L

    override fun observeTodos(): Flow<List<TodoItem>> = todos.asStateFlow()

    override fun observeTodosByDueDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TodoItem>> =
        todos.asStateFlow()
            .map { items ->
                items.filter { todo ->
                    val dueDate = todo.dueDate ?: return@filter false
                    !dueDate.isBefore(startDate) && !dueDate.isAfter(endDate)
                }
            }

    override suspend fun getTodo(id: Long): TodoItem? = todos.value.firstOrNull { it.id == id }

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
        val id = idSeed++
        val item = TodoItem(
            id = id,
            title = title,
            isDone = false,
            dueDate = dueDate,
            createdAt = now,
            updatedAt = now,
            categoryId = categoryId,
            reminderAtEpochMillis = reminderAtEpochMillis,
            isReminderEnabled = isReminderEnabled,
            reminderRepeatType = reminderRepeatType,
            reminderRepeatDaysMask = reminderRepeatDaysMask,
            dueTimeMinutes = dueTimeMinutes,
            reminderLeadMinutes = reminderLeadMinutes,
            priority = priority
        )
        todos.value = listOf(item) + todos.value
        id
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
        val existing = getTodo(id) ?: error("Todo not found")
        todos.value = todos.value.map { current ->
            if (current.id == id) {
                existing.copy(
                    title = title,
                    dueDate = dueDate,
                    updatedAt = System.currentTimeMillis(),
                    categoryId = categoryId,
                    reminderAtEpochMillis = reminderAtEpochMillis,
                    isReminderEnabled = isReminderEnabled,
                    reminderRepeatType = reminderRepeatType,
                    reminderRepeatDaysMask = reminderRepeatDaysMask,
                    dueTimeMinutes = dueTimeMinutes,
                    reminderLeadMinutes = reminderLeadMinutes,
                    priority = priority
                )
            } else {
                current
            }
        }
    }

    override suspend fun deleteTodo(id: Long): Result<Unit> = runCatching {
        todos.value = todos.value.filterNot { it.id == id }
    }

    override suspend fun toggleTodoDone(id: Long): Result<Unit> = runCatching {
        todos.value = todos.value.map { item ->
            if (item.id == id) {
                item.copy(
                    isDone = !item.isDone,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                item
            }
        }
    }

    override suspend fun syncTodos(): Result<Unit> = Result.success(Unit)

    override suspend fun getTodosWithActiveReminder(): List<TodoItem> =
        todos.value
            .asSequence()
            .filter { it.isReminderEnabled && it.reminderAtEpochMillis != null }
            .sortedBy { it.reminderAtEpochMillis }
            .toList()

    override fun observeSelectedFilter(): Flow<TodoFilter> = selectedFilter.asStateFlow()

    override suspend fun setSelectedFilter(filter: TodoFilter): Result<Unit> = runCatching {
        selectedFilter.value = filter
    }

    override fun observeCategories(): Flow<List<Category>> = categories.asStateFlow()

    override suspend fun addCategory(name: String, colorHex: String?, icon: String?): Result<Long> = runCatching {
        val normalizedName = name.trim()
        ensureCategoryNameUnique(normalizedName, null)
        val now = System.currentTimeMillis()
        val id = categoryIdSeed++
        categories.value = categories.value + Category(
            id = id,
            name = normalizedName,
            colorHex = colorHex,
            icon = icon,
            createdAt = now,
            updatedAt = now
        )
        id
    }

    override suspend fun updateCategory(id: Long, name: String, colorHex: String?, icon: String?): Result<Unit> = runCatching {
        val existing = categories.value.firstOrNull { it.id == id } ?: error("Category not found")
        val normalizedName = name.trim()
        ensureCategoryNameUnique(normalizedName, id)
        categories.value = categories.value.map { category ->
            if (category.id == id) {
                existing.copy(
                    name = normalizedName,
                    colorHex = colorHex,
                    icon = icon,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                category
            }
        }
    }

    override suspend fun deleteCategory(id: Long): Result<Unit> = runCatching {
        categories.value = categories.value.filterNot { it.id == id }
        todos.value = todos.value.map { todo ->
            if (todo.categoryId == id) todo.copy(categoryId = null, updatedAt = System.currentTimeMillis()) else todo
        }
        if (selectedCategoryFilter.value == id) {
            selectedCategoryFilter.value = null
        }
    }

    override fun observeSelectedCategoryFilter(): Flow<Long?> = selectedCategoryFilter.asStateFlow()

    override suspend fun setSelectedCategoryFilter(categoryId: Long?): Result<Unit> = runCatching {
        validateCategoryId(categoryId)
        selectedCategoryFilter.value = categoryId
    }

    override fun observeSelectedPriorityFilter(): Flow<TodoPriorityFilter> =
        selectedPriorityFilter.asStateFlow()

    override suspend fun setSelectedPriorityFilter(filter: TodoPriorityFilter): Result<Unit> = runCatching {
        selectedPriorityFilter.value = filter
    }

    private fun validateCategoryId(categoryId: Long?) {
        if (categoryId != null && categories.value.none { it.id == categoryId }) {
            error("Category not found")
        }
    }

    private fun ensureCategoryNameUnique(name: String, excludeId: Long?) {
        val normalized = name.lowercase(Locale.ROOT)
        val duplicate = categories.value.firstOrNull {
            it.id != excludeId && it.name.lowercase(Locale.ROOT) == normalized
        }
        if (duplicate != null) {
            error("Category name already exists")
        }
    }
}
