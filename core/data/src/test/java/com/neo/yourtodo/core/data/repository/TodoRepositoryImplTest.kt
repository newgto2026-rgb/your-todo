package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoCategoryFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class TodoRepositoryImplTest {

    @Test
    fun `observeTodos maps entities to domain`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(1L, "a", false, LocalDate.of(2026, 4, 1).toEpochDay(), 1L, 1L, 10L),
                TodoEntity(2L, "b", true, null, 2L, 2L, null)
            )
        }
        val repository = TodoRepositoryImpl(todoDao, FakeCategoryDao(), FakePreferencesDataSource())

        val items = repository.observeTodos().first()

        assertThat(items).containsExactly(
            TodoItem(1L, "a", false, LocalDate.of(2026, 4, 1), 1L, 1L, 10L),
            TodoItem(2L, "b", true, null, 2L, 2L, null)
        )
    }

    @Test
    fun `observeTodosByDueDateRange filters and maps entities`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(1L, "in-range-start", false, LocalDate.of(2026, 4, 1).toEpochDay(), 100L, 100L, null),
                TodoEntity(2L, "out-before", false, LocalDate.of(2026, 3, 31).toEpochDay(), 200L, 200L, null),
                TodoEntity(3L, "in-range-end", true, LocalDate.of(2026, 4, 30).toEpochDay(), 300L, 300L, null),
                TodoEntity(4L, "no-date", false, null, 400L, 400L, null),
                TodoEntity(5L, "out-after", false, LocalDate.of(2026, 5, 1).toEpochDay(), 500L, 500L, null)
            )
        }
        val repository = TodoRepositoryImpl(todoDao, FakeCategoryDao(), FakePreferencesDataSource())

        val items = repository.observeTodosByDueDateRange(
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 30)
        ).first()

        assertThat(items.map { it.title }).containsExactly("in-range-start", "in-range-end").inOrder()
        assertThat(items.map { it.dueDate }).containsExactly(
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 4, 30)
        ).inOrder()
    }

    @Test
    fun `addTodo inserts entity and returns id`() = runTest {
        val todoDao = FakeTodoDao()
        val categoryDao = FakeCategoryDao().apply {
            seed(CategoryEntity(id = 5L, name = "Work", colorHex = null, icon = null, createdAt = 1L, updatedAt = 1L))
        }
        val repository = TodoRepositoryImpl(todoDao, categoryDao, FakePreferencesDataSource())

        val result = repository.addTodo(
            title = "new",
            dueDate = LocalDate.of(2026, 4, 9),
            categoryId = 5L,
            dueTimeMinutes = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = com.neo.yourtodo.core.model.ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = null
        )

        assertThat(result.isSuccess).isTrue()
        val insertedId = result.getOrNull()!!
        assertThat(insertedId).isEqualTo(1L)
        val saved = todoDao.getTodoById(insertedId)!!
        assertThat(saved.title).isEqualTo("new")
        assertThat(saved.isDone).isFalse()
        assertThat(saved.dueDateEpochDay).isEqualTo(LocalDate.of(2026, 4, 9).toEpochDay())
        assertThat(saved.categoryId).isEqualTo(5L)
    }

    @Test
    fun `addTodo fails when category not found`() = runTest {
        val repository = TodoRepositoryImpl(FakeTodoDao(), FakeCategoryDao(), FakePreferencesDataSource())

        val result = repository.addTodo(
            title = "new",
            dueDate = null,
            categoryId = 999L,
            dueTimeMinutes = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = com.neo.yourtodo.core.model.ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = null
        )

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `updateTodo updates title due date and category`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(TodoEntity(5L, "before", false, null, 1L, 1L, null))
        }
        val categoryDao = FakeCategoryDao().apply {
            seed(CategoryEntity(id = 9L, name = "Personal", colorHex = null, icon = null, createdAt = 1L, updatedAt = 1L))
        }
        val repository = TodoRepositoryImpl(todoDao, categoryDao, FakePreferencesDataSource())

        val result = repository.updateTodo(
            id = 5L,
            title = "after",
            dueDate = LocalDate.of(2026, 4, 10),
            categoryId = 9L,
            dueTimeMinutes = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = com.neo.yourtodo.core.model.ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = null
        )

        assertThat(result.isSuccess).isTrue()
        val updated = todoDao.getTodoById(5L)!!
        assertThat(updated.title).isEqualTo("after")
        assertThat(updated.dueDateEpochDay).isEqualTo(LocalDate.of(2026, 4, 10).toEpochDay())
        assertThat(updated.categoryId).isEqualTo(9L)
    }

    @Test
    fun `observe and set selected todo filter works`() = runTest {
        val prefs = FakePreferencesDataSource()
        val repository = TodoRepositoryImpl(FakeTodoDao(), FakeCategoryDao(), prefs)

        repository.setSelectedFilter(TodoFilter.TODAY)

        assertThat(repository.observeSelectedFilter().first()).isEqualTo(TodoFilter.TODAY)
    }

    @Test
    fun `category crud and selected category filter works`() = runTest {
        val prefs = FakePreferencesDataSource()
        val categoryDao = FakeCategoryDao()
        val repository = TodoRepositoryImpl(FakeTodoDao(), categoryDao, prefs)

        val categoryId = repository.addCategory("Work", "#112233", "briefcase").getOrNull()!!
        assertThat(repository.observeCategories().first()).hasSize(1)

        val updateResult = repository.updateCategory(categoryId, "Work Updated", "#445566", "building")
        assertThat(updateResult.isSuccess).isTrue()
        assertThat(repository.observeCategories().first().first().name).isEqualTo("Work Updated")

        val setFilter = repository.setSelectedCategoryFilter(categoryId)
        assertThat(setFilter.isSuccess).isTrue()
        assertThat(repository.observeSelectedCategoryFilter().first()).isEqualTo(categoryId)

        val deleteResult = repository.deleteCategory(categoryId)
        assertThat(deleteResult.isSuccess).isTrue()
        assertThat(repository.observeCategories().first()).isEmpty()
        assertThat(repository.observeSelectedCategoryFilter().first()).isNull()
    }

    @Test
    fun `setSelectedCategoryFilter supports uncategorized sentinel`() = runTest {
        val repository = TodoRepositoryImpl(FakeTodoDao(), FakeCategoryDao(), FakePreferencesDataSource())

        val result = repository.setSelectedCategoryFilter(TodoCategoryFilter.UNCATEGORIZED_FILTER_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.observeSelectedCategoryFilter().first())
            .isEqualTo(TodoCategoryFilter.UNCATEGORIZED_FILTER_ID)
    }

    private class FakePreferencesDataSource : UserPreferencesDataSource {
        private val filterFlow = MutableStateFlow(TodoFilter.ALL)
        private val categoryFilterFlow = MutableStateFlow<Long?>(null)
        private val priorityFilterFlow = MutableStateFlow(TodoPriorityFilter.ALL)

        override val selectedTodoFilter: Flow<TodoFilter> = filterFlow.asStateFlow()
        override val selectedTodoCategoryFilter: Flow<Long?> = categoryFilterFlow.asStateFlow()
        override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> = priorityFilterFlow.asStateFlow()

        override suspend fun setSelectedTodoFilter(filter: TodoFilter) {
            filterFlow.value = filter
        }

        override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) {
            categoryFilterFlow.value = categoryId
        }

        override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) {
            priorityFilterFlow.value = filter
        }
    }

    private class FakeTodoDao : TodoDao {
        private val itemsFlow = MutableStateFlow<List<TodoEntity>>(emptyList())
        private var nextId: Long = 1L

        fun seed(vararg entities: TodoEntity) {
            itemsFlow.value = entities.toList()
            nextId = (entities.maxOfOrNull { it.id } ?: 0L) + 1L
        }

        override fun observeTodos(): Flow<List<TodoEntity>> = itemsFlow.asStateFlow()

        override fun observeTodosByDueDateRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TodoEntity>> =
            itemsFlow
                .asStateFlow()
                .map { items ->
                    items.filter { entity ->
                        val dueDateEpochDay = entity.dueDateEpochDay ?: return@filter false
                        dueDateEpochDay in startEpochDay..endEpochDay
                    }.sortedWith(
                        compareBy<TodoEntity> { it.dueDateEpochDay }.thenByDescending { it.createdAt }
                    )
                }

        override suspend fun insert(todo: TodoEntity): Long {
            val id = if (todo.id == 0L) nextId++ else todo.id
            val saved = todo.copy(id = id)
            itemsFlow.value = listOf(saved) + itemsFlow.value
            return id
        }

        override suspend fun update(todo: TodoEntity) {
            itemsFlow.value = itemsFlow.value.map { if (it.id == todo.id) todo else it }
        }

        override suspend fun delete(todo: TodoEntity) {
            itemsFlow.value = itemsFlow.value.filterNot { it.id == todo.id }
        }

        override suspend fun getTodoById(id: Long): TodoEntity? = itemsFlow.value.firstOrNull { it.id == id }

        override suspend fun getTodosWithActiveReminder(): List<TodoEntity> =
            itemsFlow.value
                .asSequence()
                .filter { it.isReminderEnabled && it.reminderAtEpochMillis != null }
                .sortedBy { it.reminderAtEpochMillis }
                .toList()
    }

    private class FakeCategoryDao : CategoryDao {
        private val categoriesFlow = MutableStateFlow<List<CategoryEntity>>(emptyList())
        private var nextId: Long = 1L

        fun seed(vararg categories: CategoryEntity) {
            categoriesFlow.value = categories.toList()
            nextId = (categories.maxOfOrNull { it.id } ?: 0L) + 1L
        }

        override fun observeCategories(): Flow<List<CategoryEntity>> = categoriesFlow.asStateFlow()

        override suspend fun insert(category: CategoryEntity): Long {
            val id = if (category.id == 0L) nextId++ else category.id
            val saved = category.copy(id = id)
            categoriesFlow.value = categoriesFlow.value + saved
            return id
        }

        override suspend fun update(category: CategoryEntity) {
            categoriesFlow.value = categoriesFlow.value.map { current ->
                if (current.id == category.id) category else current
            }
        }

        override suspend fun delete(category: CategoryEntity) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.id == category.id }
        }

        override suspend fun getCategoryById(id: Long): CategoryEntity? =
            categoriesFlow.value.firstOrNull { it.id == id }

        override suspend fun getCategoryByName(name: String): CategoryEntity? =
            categoriesFlow.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
