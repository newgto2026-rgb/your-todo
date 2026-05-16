package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity
import com.neo.yourtodo.core.data.di.TodoRepositoryCollaboratorModule
import com.neo.yourtodo.core.data.repository.todo.TodoCategoryStore
import com.neo.yourtodo.core.data.repository.todo.TodoFilterPreferences
import com.neo.yourtodo.core.data.repository.todo.TodoLocalTodoStore
import com.neo.yourtodo.core.data.repository.todo.TodoOutboxStore
import com.neo.yourtodo.core.data.repository.todo.TodoReminderReader
import com.neo.yourtodo.core.data.repository.todo.TodoSyncCoordinator
import com.neo.yourtodo.core.data.repository.todo.TodoSyncSessionProvider
import com.neo.yourtodo.core.data.repository.todo.TodoTimeProvider
import com.neo.yourtodo.core.data.repository.todo.TodoTransactionRunner
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoCategoryFilter
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.auth.NetworkAuthUser
import com.neo.yourtodo.core.network.auth.NetworkAuthUserResponse
import com.neo.yourtodo.core.network.sync.NetworkTodoSyncPullResponse
import com.neo.yourtodo.core.network.sync.NetworkTodo
import com.neo.yourtodo.core.network.sync.NetworkTodoMutationResult
import com.neo.yourtodo.core.network.sync.NetworkTodoSyncPushRequest
import com.neo.yourtodo.core.network.sync.NetworkTodoSyncPushResponse
import com.neo.yourtodo.core.network.sync.TodoSyncAuthRequiredException
import com.neo.yourtodo.core.network.sync.TodoSyncNetworkDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
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
        val repository = repository(todoDao = todoDao)

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
        val repository = repository(todoDao = todoDao)

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
    fun `getTodosWithActiveReminder maps active reminder entities`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 1L,
                    title = "disabled",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    reminderAtEpochMillis = 100L,
                    isReminderEnabled = false
                ),
                TodoEntity(
                    id = 2L,
                    title = "active later",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 2L,
                    updatedAt = 2L,
                    categoryId = null,
                    reminderAtEpochMillis = 200L,
                    isReminderEnabled = true
                ),
                TodoEntity(
                    id = 3L,
                    title = "active earlier",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 3L,
                    updatedAt = 3L,
                    categoryId = null,
                    reminderAtEpochMillis = 100L,
                    isReminderEnabled = true
                )
            )
        }
        val repository = repository(todoDao = todoDao)

        val reminders = repository.getTodosWithActiveReminder()

        assertThat(reminders.map { it.title }).containsExactly("active earlier", "active later").inOrder()
        assertThat(reminders.map { it.reminderAtEpochMillis }).containsExactly(100L, 200L).inOrder()
    }

    @Test
    fun `addTodo inserts entity and returns id`() = runTest {
        val todoDao = FakeTodoDao()
        val categoryDao = FakeCategoryDao().apply {
            seed(CategoryEntity(id = 5L, name = "Work", colorHex = null, icon = null, createdAt = 1L, updatedAt = 1L))
        }
        val repository = repository(todoDao = todoDao, categoryDao = categoryDao)

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
        val repository = repository()

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
        val repository = repository(todoDao = todoDao, categoryDao = categoryDao)

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
    fun `updateTodo rethrows cancellation and preserves existing todo`() = runTest {
        val cancellation = CancellationException("cancelled")
        val todoDao = FakeTodoDao().apply {
            seed(TodoEntity(5L, "before", false, null, 1L, 1L, null))
            updateFailure = cancellation
        }
        val repository = repository(todoDao = todoDao)

        val thrown = runCatching {
            repository.updateTodo(5L, "after", null, null)
        }.exceptionOrNull()

        assertThat(thrown).isSameInstanceAs(cancellation)
        assertThat(todoDao.getTodoById(5L)?.title).isEqualTo("before")
    }

    @Test
    fun `observe and set selected todo filter works`() = runTest {
        val prefs = FakePreferencesDataSource()
        val repository = repository(prefs = prefs)

        repository.setSelectedFilter(TodoFilter.TODAY)

        assertThat(repository.observeSelectedFilter().first()).isEqualTo(TodoFilter.TODAY)
    }

    @Test
    fun `observe and set selected todo sort option works`() = runTest {
        val prefs = FakePreferencesDataSource()
        val repository = repository(prefs = prefs)

        repository.setSelectedSortOption(TodoSortOption.PRIORITY)

        assertThat(repository.observeSelectedSortOption().first()).isEqualTo(TodoSortOption.PRIORITY)
    }

    @Test
    fun `observe and set selected todo priority filter works`() = runTest {
        val prefs = FakePreferencesDataSource()
        val repository = repository(prefs = prefs)

        repository.setSelectedPriorityFilter(TodoPriorityFilter.HIGH)

        assertThat(repository.observeSelectedPriorityFilter().first()).isEqualTo(TodoPriorityFilter.HIGH)
    }

    @Test
    fun `setSelectedSortOption rethrows cancellation exception`() = runTest {
        val prefs = FakePreferencesDataSource().apply {
            sortOptionFailure = CancellationException("cancelled")
        }
        val repository = repository(prefs = prefs)

        val thrown = runCatching {
            repository.setSelectedSortOption(TodoSortOption.PRIORITY)
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
        assertThat(repository.observeSelectedSortOption().first()).isEqualTo(TodoSortOption.DEFAULT)
    }

    @Test
    fun `category crud and selected category filter works`() = runTest {
        val prefs = FakePreferencesDataSource()
        val categoryDao = FakeCategoryDao()
        val repository = repository(categoryDao = categoryDao, prefs = prefs)

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
    fun `addCategory rejects duplicate name case insensitively`() = runTest {
        val repository = repository()

        val first = repository.addCategory("Work", null, null)
        val duplicate = repository.addCategory("work", null, null)

        assertThat(first.isSuccess).isTrue()
        assertThat(duplicate.isFailure).isTrue()
        assertThat(repository.observeCategories().first().map { it.name }).containsExactly("Work")
    }

    @Test
    fun `setSelectedCategoryFilter supports uncategorized sentinel`() = runTest {
        val repository = repository()

        val result = repository.setSelectedCategoryFilter(TodoCategoryFilter.UNCATEGORIZED_FILTER_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.observeSelectedCategoryFilter().first())
            .isEqualTo(TodoCategoryFilter.UNCATEGORIZED_FILTER_ID)
    }

    @Test
    fun `addTodo with auth creates pending todo and create outbox`() = runTest {
        val todoDao = FakeTodoDao()
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val transactionRunner = FakeTodoTransactionRunner()
        val timeProvider = FixedTodoTimeProvider(123_456L)
        val repository = repository(
            todoDao = todoDao,
            outboxDao = outboxDao,
            prefs = prefs,
            transactionRunner = transactionRunner,
            timeProvider = timeProvider
        )

        val id = repository.addTodo(
            title = "sync me",
            dueDate = LocalDate.of(2026, 5, 10),
            categoryId = null,
            priority = TodoPriority.HIGH
        ).getOrThrow()

        val saved = todoDao.getTodoById(id)!!
        assertThat(saved.syncStatus).isEqualTo("PENDING_CREATE")
        assertThat(saved.ownerUserId).isEqualTo("user-id")
        assertThat(saved.clientId).isNotNull()
        assertThat(saved.createdAt).isEqualTo(123_456L)
        assertThat(saved.updatedAt).isEqualTo(123_456L)
        assertThat(outboxDao.items).hasSize(1)
        assertThat(outboxDao.items.single().type).isEqualTo("CREATE")
        assertThat(outboxDao.items.single().createdAt).isEqualTo(123_456L)
        assertThat(outboxDao.items.single().payloadJson).contains("sync me")
        assertThat(outboxDao.items.single().payloadJson).contains("\"priority\":\"HIGH\"")
        assertThat(transactionRunner.transactionCount).isEqualTo(1)
    }

    @Test
    fun `pendingCreate edit merges create payload`() = runTest {
        val todoDao = FakeTodoDao()
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs)
        val id = repository.addTodo("before", null, null).getOrThrow()

        repository.updateTodo(id, "after", LocalDate.of(2026, 5, 11), null)

        val saved = todoDao.getTodoById(id)!!
        assertThat(saved.title).isEqualTo("after")
        assertThat(saved.syncStatus).isEqualTo("PENDING_CREATE")
        assertThat(outboxDao.items).hasSize(1)
        assertThat(outboxDao.items.single().type).isEqualTo("CREATE")
        assertThat(outboxDao.items.single().payloadJson).contains("after")
        assertThat(outboxDao.items.single().payloadJson).doesNotContain("before")
    }

    @Test
    fun `pendingCreate delete removes todo and outbox`() = runTest {
        val todoDao = FakeTodoDao()
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs)
        val id = repository.addTodo("temp", null, null).getOrThrow()

        val result = repository.deleteTodo(id)

        assertThat(result.isSuccess).isTrue()
        assertThat(todoDao.getTodoById(id)).isNull()
        assertThat(outboxDao.items).isEmpty()
    }

    @Test
    fun `pendingUpdate delete replaces update with delete outbox`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 7L,
                    title = "server todo",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    serverId = "server-1",
                    clientId = "client-1",
                    ownerUserId = "user-id",
                    syncStatus = "SYNCED",
                    serverRevision = "1"
                )
            )
        }
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs)

        repository.updateTodo(7L, "edited", null, null)
        repository.deleteTodo(7L)

        val saved = todoDao.getTodoById(7L)!!
        assertThat(saved.syncStatus).isEqualTo("PENDING_DELETE")
        assertThat(outboxDao.items).hasSize(1)
        assertThat(outboxDao.items.single().type).isEqualTo("DELETE")
    }

    @Test
    fun `synced update creates single update outbox`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 9L,
                    title = "server todo",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    serverId = "server-9",
                    clientId = "client-9",
                    ownerUserId = "user-id",
                    syncStatus = "SYNCED",
                    serverRevision = "1"
                )
            )
        }
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs)

        val result = repository.updateTodo(9L, "edited once", LocalDate.of(2026, 5, 12), null)

        assertThat(result.isSuccess).isTrue()
        val saved = todoDao.getTodoById(9L)!!
        assertThat(saved.title).isEqualTo("edited once")
        assertThat(saved.syncStatus).isEqualTo("PENDING_UPDATE")
        assertThat(outboxDao.items).hasSize(1)
        assertThat(outboxDao.items.single().type).isEqualTo("UPDATE")
        assertThat(outboxDao.items.single().payloadJson).contains("edited once")
    }

    @Test
    fun `synced update without remote identity falls back to local only without outbox`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 12L,
                    title = "orphan synced todo",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    serverId = null,
                    clientId = "client-12",
                    ownerUserId = "user-id",
                    syncStatus = "SYNCED",
                    serverRevision = "1"
                )
            )
        }
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs)

        val result = repository.updateTodo(12L, "edited local fallback", null, null)

        assertThat(result.isSuccess).isTrue()
        val saved = todoDao.getTodoById(12L)!!
        assertThat(saved.title).isEqualTo("edited local fallback")
        assertThat(saved.syncStatus).isEqualTo("LOCAL_ONLY")
        assertThat(outboxDao.items).isEmpty()
    }

    @Test
    fun `synced delete without remote identity deletes locally without outbox`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 13L,
                    title = "orphan delete todo",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    serverId = "server-13",
                    clientId = "client-13",
                    ownerUserId = null,
                    syncStatus = "SYNCED",
                    serverRevision = "1"
                )
            )
        }
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs)

        val result = repository.deleteTodo(13L)

        assertThat(result.isSuccess).isTrue()
        assertThat(todoDao.getTodoById(13L)).isNull()
        assertThat(outboxDao.items).isEmpty()
    }

    @Test
    fun `pendingUpdate second update merges update payload`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 10L,
                    title = "server todo",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    serverId = "server-10",
                    clientId = "client-10",
                    ownerUserId = "user-id",
                    syncStatus = "SYNCED",
                    serverRevision = "1"
                )
            )
        }
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs)

        repository.updateTodo(10L, "first edit", null, null)
        repository.updateTodo(10L, "second edit", null, null)

        assertThat(todoDao.getTodoById(10L)?.title).isEqualTo("second edit")
        assertThat(outboxDao.items).hasSize(1)
        assertThat(outboxDao.items.single().type).isEqualTo("UPDATE")
        assertThat(outboxDao.items.single().payloadJson).contains("second edit")
        assertThat(outboxDao.items.single().payloadJson).doesNotContain("first edit")
    }

    @Test
    fun `pendingDelete update is blocked and keeps delete outbox`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 11L,
                    title = "deleting",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    serverId = "server-11",
                    clientId = "client-11",
                    ownerUserId = "user-id",
                    syncStatus = "SYNCED",
                    serverRevision = "1"
                )
            )
        }
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs)
        repository.deleteTodo(11L)

        val result = repository.updateTodo(11L, "should not apply", null, null)

        assertThat(result.isFailure).isTrue()
        val saved = todoDao.getTodoById(11L)!!
        assertThat(saved.title).isEqualTo("deleting")
        assertThat(saved.syncStatus).isEqualTo("PENDING_DELETE")
        assertThat(outboxDao.items).hasSize(1)
        assertThat(outboxDao.items.single().type).isEqualTo("DELETE")
    }

    @Test
    fun `duplicate client id sync result converges and clears outbox`() = runTest {
        val todoDao = FakeTodoDao()
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeTodoSyncNetworkDataSource()
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs, network = network)
        val id = repository.addTodo("retry create", null, null).getOrThrow()
        val clientId = todoDao.getTodoById(id)!!.clientId!!
        network.nextPushResponse = NetworkTodoSyncPushResponse(
            results = listOf(
                NetworkTodoMutationResult(
                    clientMutationId = outboxDao.items.single().clientMutationId,
                    status = "DUPLICATE_CLIENT_ID",
                    todo = networkTodo(id = "server-1", clientId = clientId, title = "retry create", revision = "2")
                )
            ),
            nextCursor = "2"
        )

        val result = repository.syncTodos()

        assertThat(result.isSuccess).isTrue()
        val saved = todoDao.getTodoById(id)!!
        assertThat(saved.syncStatus).isEqualTo("SYNCED")
        assertThat(saved.serverId).isEqualTo("server-1")
        assertThat(saved.serverRevision).isEqualTo("2")
        assertThat(outboxDao.items).isEmpty()
        assertThat(prefs.todoSyncCursor.first()).isEqualTo("2")
    }

    @Test
    fun `sync create keeps local priority when server response omits priority`() = runTest {
        val todoDao = FakeTodoDao()
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeTodoSyncNetworkDataSource()
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs, network = network)
        val id = repository.addTodo(
            title = "high priority",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.HIGH
        ).getOrThrow()
        val clientId = todoDao.getTodoById(id)!!.clientId!!
        network.nextPushResponse = NetworkTodoSyncPushResponse(
            results = listOf(
                NetworkTodoMutationResult(
                    clientMutationId = outboxDao.items.single().clientMutationId,
                    status = "APPLIED",
                    todo = networkTodo(id = "server-high", clientId = clientId, title = "high priority", revision = "2")
                )
            ),
            nextCursor = "2"
        )

        val result = repository.syncTodos()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.lastPushRequest?.mutations?.single()?.payload?.priority).isEqualTo("HIGH")
        assertThat(todoDao.getTodoById(id)?.priority).isEqualTo(TodoPriority.HIGH.name)
        assertThat(todoDao.getTodoById(id)?.syncStatus).isEqualTo("SYNCED")
        assertThat(outboxDao.items).isEmpty()
    }

    @Test
    fun `sync legacy outbox without priority uses local priority`() = runTest {
        val todoDao = FakeTodoDao()
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeTodoSyncNetworkDataSource()
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs, network = network)
        val clientId = "legacy-client-id"
        todoDao.seed(
            TodoEntity(
                id = 10L,
                title = "legacy high",
                isDone = false,
                dueDateEpochDay = null,
                createdAt = 1L,
                updatedAt = 1L,
                categoryId = null,
                priority = TodoPriority.HIGH.name,
                clientId = clientId,
                ownerUserId = "user-id",
                syncStatus = "PENDING_CREATE"
            )
        )
        outboxDao.insert(
            TodoOutboxEntity(
                ownerUserId = "user-id",
                clientMutationId = "legacy-mutation-id",
                todoLocalId = 10L,
                serverId = null,
                clientId = clientId,
                type = "CREATE",
                payloadJson = """{"title":"legacy high","status":"ACTIVE"}""",
                createdAt = 1L
            )
        )
        network.nextPushResponse = NetworkTodoSyncPushResponse(
            results = listOf(
                NetworkTodoMutationResult(
                    clientMutationId = "legacy-mutation-id",
                    status = "APPLIED",
                    todo = networkTodo(id = "server-legacy", clientId = clientId, title = "legacy high", revision = "2")
                )
            ),
            nextCursor = "2"
        )

        val result = repository.syncTodos()

        assertThat(result.isSuccess).isTrue()
        assertThat(todoDao.getTodosByIdsRequests).containsExactly(listOf(10L))
        assertThat(todoDao.getTodoByIdCallCount).isEqualTo(0)
        assertThat(network.lastPushRequest?.mutations?.single()?.payload?.priority).isEqualTo("HIGH")
        assertThat(todoDao.getTodoById(10L)?.priority).isEqualTo(TodoPriority.HIGH.name)
        assertThat(outboxDao.items).isEmpty()
    }

    @Test
    fun `sync pull maps remote priority case insensitively`() = runTest {
        val todoDao = FakeTodoDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeTodoSyncNetworkDataSource().apply {
            nextPullResponse = NetworkTodoSyncPullResponse(
                todos = listOf(
                    networkTodo(
                        id = "server-case",
                        clientId = "client-case",
                        title = "case",
                        revision = "2",
                        priority = "high"
                    )
                ),
                nextCursor = "2"
            )
        }
        val repository = repository(todoDao = todoDao, prefs = prefs, network = network)

        val result = repository.syncTodos()

        assertThat(result.isSuccess).isTrue()
        assertThat(todoDao.getTodoByServerId("user-id", "server-case")?.priority).isEqualTo(TodoPriority.HIGH.name)
    }

    @Test
    fun `rejected deleted sync result applies tombstone and clears outbox`() = runTest {
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 8L,
                    title = "stale server todo",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    serverId = "server-deleted",
                    clientId = "client-deleted",
                    ownerUserId = "user-id",
                    syncStatus = "SYNCED",
                    serverRevision = "1"
                )
            )
        }
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeTodoSyncNetworkDataSource()
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs, network = network)
        repository.updateTodo(8L, "edited stale todo", null, null)
        network.nextPushResponse = NetworkTodoSyncPushResponse(
            results = listOf(
                NetworkTodoMutationResult(
                    clientMutationId = outboxDao.items.single().clientMutationId,
                    status = "REJECTED_DELETED",
                    todo = networkTodo(
                        id = "server-deleted",
                        clientId = "client-deleted",
                        title = "stale server todo",
                        revision = "3",
                        status = "DELETED"
                    )
                )
            ),
            nextCursor = "3"
        )

        val result = repository.syncTodos()

        assertThat(result.isSuccess).isTrue()
        val saved = todoDao.getTodoById(8L)!!
        assertThat(saved.serverRevision).isEqualTo("3")
        assertThat(saved.deletedAt).isNotNull()
        assertThat(saved.syncStatus).isEqualTo("SYNCED")
        assertThat(outboxDao.items).isEmpty()
    }

    @Test
    fun `auth required sync preserves outbox and records halt reason`() = runTest {
        val todoDao = FakeTodoDao()
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeTodoSyncNetworkDataSource().apply { authRequired = true }
        val repository = repository(todoDao = todoDao, outboxDao = outboxDao, prefs = prefs, network = network)
        val id = repository.addTodo("offline after token expiry", null, null).getOrThrow()

        val result = repository.syncTodos()

        assertThat(result.isFailure).isTrue()
        assertThat(todoDao.getTodoById(id)?.syncStatus).isEqualTo("PENDING_CREATE")
        assertThat(outboxDao.items).hasSize(1)
        assertThat(prefs.todoSyncHaltReason.first()).isEqualTo("AUTH_REQUIRED")
        assertThat(prefs.authSession.first()).isNull()
    }

    @Test
    fun `auth required sync refreshes session and retries pending outbox`() = runTest {
        val todoDao = FakeTodoDao()
        val outboxDao = FakeTodoOutboxDao()
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeTodoSyncNetworkDataSource().apply {
            authFailuresRemaining = 1
            nextPushResponse = NetworkTodoSyncPushResponse(
                results = listOf(
                    NetworkTodoMutationResult(
                        clientMutationId = "mutation",
                        status = "APPLIED",
                        todo = networkTodo(
                            id = "server-id",
                            clientId = "client-id",
                            title = "after refresh",
                            revision = "1"
                        ),
                        error = null
                    )
                ),
                nextCursor = "1"
            )
        }
        val authNetwork = FakeAuthNetworkDataSource()
        val repository = repository(
            todoDao = todoDao,
            outboxDao = outboxDao,
            prefs = prefs,
            network = network,
            authNetwork = authNetwork
        )
        val id = repository.addTodo("after refresh", null, null).getOrThrow()
        val local = todoDao.getTodoById(id)!!
        outboxDao.update(outboxDao.items.single().copy(clientMutationId = "mutation"))
        todoDao.update(local.copy(clientId = "client-id"))

        val result = repository.syncTodos()

        assertThat(result.isSuccess).isTrue()
        assertThat(authNetwork.lastRefreshToken).isEqualTo("refresh-token")
        assertThat(prefs.authSession.first()?.accessToken).isEqualTo("refreshed-access-token")
        assertThat(prefs.authSession.first()?.refreshToken).isEqualTo("refreshed-refresh-token")
        assertThat(prefs.todoSyncHaltReason.first()).isNull()
        assertThat(outboxDao.items).isEmpty()
        assertThat(todoDao.getTodoById(id)?.syncStatus).isEqualTo("SYNCED")
    }

    private fun authSession(): AuthSessionData =
        AuthSessionData(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            userId = "user-id",
            nickname = "neo",
            email = "neo@example.com",
            onboardingRequired = false
        )

    private fun networkTodo(
        id: String,
        clientId: String,
        title: String,
        revision: String,
        status: String = "ACTIVE",
        priority: String? = null
    ): NetworkTodo =
        NetworkTodo(
            id = id,
            clientId = clientId,
            title = title,
            dueDate = null,
            status = status,
            priority = priority,
            revision = revision,
            createdAt = "2026-05-08T00:00:00.000Z",
            updatedAt = "2026-05-08T00:00:00.000Z",
            deletedAt = if (status == "DELETED") "2026-05-08T00:00:00.000Z" else null
        )

    private fun repository(
        todoDao: FakeTodoDao = FakeTodoDao(),
        categoryDao: FakeCategoryDao = FakeCategoryDao(),
        outboxDao: FakeTodoOutboxDao = FakeTodoOutboxDao(),
        prefs: FakePreferencesDataSource = FakePreferencesDataSource(),
        network: FakeTodoSyncNetworkDataSource = FakeTodoSyncNetworkDataSource(),
        authNetwork: FakeAuthNetworkDataSource = FakeAuthNetworkDataSource(),
        transactionRunner: TodoTransactionRunner = FakeTodoTransactionRunner(),
        timeProvider: TodoTimeProvider = FixedTodoTimeProvider(1_000L)
    ): TodoRepositoryImpl {
        val json = TodoRepositoryCollaboratorModule.provideTodoSyncPayloadJson()
        val categoryStore = TodoCategoryStore(categoryDao, prefs)
        val syncSessionProvider = TodoSyncSessionProvider(prefs)
        val outboxStore = TodoOutboxStore(outboxDao, timeProvider, json)
        return TodoRepositoryImpl(
            todos = TodoLocalTodoStore(
                todoDao = todoDao,
                categoryStore = categoryStore,
                outboxStore = outboxStore,
                syncSessionProvider = syncSessionProvider,
                transactionRunner = transactionRunner,
                timeProvider = timeProvider
            ),
            categoryStore = categoryStore,
            filterPreferences = TodoFilterPreferences(prefs, categoryStore),
            reminderReader = TodoReminderReader(todoDao),
            syncCoordinator = TodoSyncCoordinator(
                todoDao = todoDao,
                outboxStore = outboxStore,
                userPreferencesDataSource = prefs,
                todoSyncNetworkDataSource = network,
                authSessionRefresher = AuthSessionRefresher(
                    prefs,
                    authNetwork
                ),
                syncSessionProvider = syncSessionProvider,
                json = json
            )
        )
    }

    private class FakeTodoTransactionRunner : TodoTransactionRunner {
        var transactionCount = 0

        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            transactionCount += 1
            return block()
        }
    }

    private class FixedTodoTimeProvider(private val now: Long) : TodoTimeProvider {
        override fun currentTimeMillis(): Long = now
    }

    private class FakePreferencesDataSource : UserPreferencesDataSource {
        private val authSessionFlow = MutableStateFlow<AuthSessionData?>(null)
        private val filterFlow = MutableStateFlow(TodoFilter.ALL)
        private val categoryFilterFlow = MutableStateFlow<Long?>(null)
        private val priorityFilterFlow = MutableStateFlow(TodoPriorityFilter.ALL)
        private val sortOptionFlow = MutableStateFlow(TodoSortOption.DEFAULT)
        private val syncCursorFlow = MutableStateFlow<String?>(null)
        private val syncHaltReasonFlow = MutableStateFlow<String?>(null)
        var sortOptionFailure: Throwable? = null

        override val authSession: Flow<AuthSessionData?> = authSessionFlow.asStateFlow()
        override val selectedTodoFilter: Flow<TodoFilter> = filterFlow.asStateFlow()
        override val selectedTodoCategoryFilter: Flow<Long?> = categoryFilterFlow.asStateFlow()
        override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> = priorityFilterFlow.asStateFlow()
        override val selectedTodoSortOption: Flow<TodoSortOption> = sortOptionFlow.asStateFlow()
        override val todoSyncCursor: Flow<String?> = syncCursorFlow.asStateFlow()
        override val todoSyncHaltReason: Flow<String?> = syncHaltReasonFlow.asStateFlow()

        override suspend fun saveAuthSession(session: AuthSessionData) {
            authSessionFlow.value = session
        }

        override suspend fun clearAuthSession() {
            authSessionFlow.value = null
        }

        override suspend fun setSelectedTodoFilter(filter: TodoFilter) {
            filterFlow.value = filter
        }

        override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) {
            categoryFilterFlow.value = categoryId
        }

        override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) {
            priorityFilterFlow.value = filter
        }

        override suspend fun setSelectedTodoSortOption(option: TodoSortOption) {
            sortOptionFailure?.let { throw it }
            sortOptionFlow.value = option
        }

        override suspend fun setTodoSyncCursor(cursor: String?) {
            syncCursorFlow.value = cursor
        }

        override suspend fun setTodoSyncHaltReason(reason: String?) {
            syncHaltReasonFlow.value = reason
        }

        override suspend fun clearTodoSyncState() {
            syncCursorFlow.value = null
            syncHaltReasonFlow.value = null
        }
    }

    private class FakeTodoDao : TodoDao {
        private val itemsFlow = MutableStateFlow<List<TodoEntity>>(emptyList())
        private var nextId: Long = 1L
        val getTodosByIdsRequests = mutableListOf<List<Long>>()
        var getTodoByIdCallCount = 0
        var updateFailure: Throwable? = null

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
            updateFailure?.let { throw it }
            itemsFlow.value = itemsFlow.value.map { if (it.id == todo.id) todo else it }
        }

        override suspend fun delete(todo: TodoEntity) {
            itemsFlow.value = itemsFlow.value.filterNot { it.id == todo.id }
        }

        override suspend fun getTodoById(id: Long): TodoEntity? {
            getTodoByIdCallCount += 1
            return itemsFlow.value.firstOrNull { it.id == id }
        }

        override suspend fun getTodosByIds(ids: List<Long>): List<TodoEntity> {
            getTodosByIdsRequests += ids
            return itemsFlow.value.filter { it.id in ids }
        }

        override suspend fun getTodoByServerId(ownerUserId: String, serverId: String): TodoEntity? =
            itemsFlow.value.firstOrNull { it.ownerUserId == ownerUserId && it.serverId == serverId }

        override suspend fun getTodoByClientId(ownerUserId: String, clientId: String): TodoEntity? =
            itemsFlow.value.firstOrNull { it.ownerUserId == ownerUserId && it.clientId == clientId }

        override suspend fun deleteSyncedTodosByOwner(ownerUserId: String) {
            itemsFlow.value = itemsFlow.value.filterNot {
                it.ownerUserId == ownerUserId && it.syncStatus != "LOCAL_ONLY"
            }
        }

        override suspend fun deleteByOwner(ownerUserId: String) {
            itemsFlow.value = itemsFlow.value.filterNot { it.ownerUserId == ownerUserId }
        }

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

    private class FakeTodoOutboxDao : TodoOutboxDao {
        val items = mutableListOf<TodoOutboxEntity>()
        private var nextId = 1L

        override suspend fun getPendingMutations(ownerUserId: String): List<TodoOutboxEntity> =
            items.filter { it.ownerUserId == ownerUserId }.sortedWith(compareBy<TodoOutboxEntity> { it.createdAt }.thenBy { it.id })

        override suspend fun getByTodoLocalId(todoLocalId: Long): TodoOutboxEntity? =
            items.firstOrNull { it.todoLocalId == todoLocalId }

        override suspend fun insert(outbox: TodoOutboxEntity): Long {
            val id = if (outbox.id == 0L) nextId++ else outbox.id
            items.removeAll { it.id == id || it.clientMutationId == outbox.clientMutationId }
            items += outbox.copy(id = id)
            return id
        }

        override suspend fun update(outbox: TodoOutboxEntity) {
            items.replaceAll { if (it.id == outbox.id) outbox else it }
        }

        override suspend fun delete(outbox: TodoOutboxEntity) {
            items.removeAll { it.id == outbox.id }
        }

        override suspend fun deleteById(id: Long) {
            items.removeAll { it.id == id }
        }

        override suspend fun deleteByTodoLocalId(todoLocalId: Long) {
            items.removeAll { it.todoLocalId == todoLocalId }
        }

        override suspend fun deleteByOwner(ownerUserId: String) {
            items.removeAll { it.ownerUserId == ownerUserId }
        }
    }

    private class FakeAuthNetworkDataSource : AuthNetworkDataSource {
        var lastRefreshToken: String? = null

        override suspend fun signInWithGoogle(idToken: String): NetworkAuthSession =
            refreshedSession()

        override suspend fun refreshSession(refreshToken: String): NetworkAuthSession {
            lastRefreshToken = refreshToken
            return refreshedSession()
        }

        override suspend fun completeNicknameOnboarding(
            accessToken: String,
            nickname: String
        ): NetworkAuthUserResponse =
            NetworkAuthUserResponse(
                user = refreshedSession().user.copy(nickname = nickname)
            )

        private fun refreshedSession() =
            NetworkAuthSession(
                accessToken = "refreshed-access-token",
                refreshToken = "refreshed-refresh-token",
                user = NetworkAuthUser(
                    id = "user-id",
                    nickname = "neo",
                    email = "neo@example.com",
                    onboardingRequired = false
                )
            )
    }

    private class FakeTodoSyncNetworkDataSource : TodoSyncNetworkDataSource {
        var nextPullResponse = NetworkTodoSyncPullResponse(todos = emptyList(), nextCursor = "0")
        var nextPushResponse = NetworkTodoSyncPushResponse(results = emptyList(), nextCursor = "0")
        var lastPushRequest: NetworkTodoSyncPushRequest? = null
        var authRequired = false
        var authFailuresRemaining = 0

        override suspend fun pullTodos(accessToken: String, cursor: String?): NetworkTodoSyncPullResponse {
            if (shouldFailAuth()) throw TodoSyncAuthRequiredException()
            return if (nextPullResponse.todos.isEmpty()) {
                nextPullResponse.copy(nextCursor = cursor ?: nextPullResponse.nextCursor)
            } else {
                nextPullResponse
            }
        }

        override suspend fun pushTodos(
            accessToken: String,
            request: NetworkTodoSyncPushRequest
        ): NetworkTodoSyncPushResponse {
            if (shouldFailAuth()) throw TodoSyncAuthRequiredException()
            lastPushRequest = request
            return nextPushResponse
        }

        private fun shouldFailAuth(): Boolean {
            if (authRequired) return true
            if (authFailuresRemaining <= 0) return false
            authFailuresRemaining -= 1
            return true
        }
    }
}
