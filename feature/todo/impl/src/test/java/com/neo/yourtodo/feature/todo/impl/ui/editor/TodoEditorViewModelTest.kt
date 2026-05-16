package com.neo.yourtodo.feature.todo.impl.ui.editor

import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.scheduler.TodoReminderScheduler
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.usecase.AddTodoUseCase
import com.neo.yourtodo.core.domain.usecase.DeleteTodoUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.GetTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ManageAssignedTodoUseCase
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateTodoUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceRefreshClock
import com.neo.yourtodo.core.domain.usecase.WorkspaceRefreshPolicy
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoReminder
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoUser
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import com.neo.yourtodo.feature.todo.impl.R
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeTodoRepository
    private lateinit var assignmentRepository: FakeAssignmentRepository
    private lateinit var reminderScheduler: RecordingReminderScheduler
    private lateinit var calendarWidgetUpdater: RecordingCalendarWidgetUpdater
    private lateinit var viewModel: TodoEditorViewModel

    @Before
    fun setUp() {
        repository = FakeTodoRepository()
        assignmentRepository = FakeAssignmentRepository()
        reminderScheduler = RecordingReminderScheduler()
        calendarWidgetUpdater = RecordingCalendarWidgetUpdater()
        viewModel = TodoEditorViewModel(
            addTodoUseCase = AddTodoUseCase(repository),
            updateTodoUseCase = UpdateTodoUseCase(repository),
            deleteTodoUseCase = DeleteTodoUseCase(repository),
            getTodoUseCase = GetTodoUseCase(repository),
            getAssignedTodosUseCase = GetAssignedTodosUseCase(assignmentRepository),
            manageAssignedTodoUseCase = ManageAssignedTodoUseCase(
                assignmentRepository,
                RefreshWorkspaceUseCase(
                    todoRepository = repository,
                    friendRepository = FakeFriendRepository(),
                    assignmentRepository = assignmentRepository,
                    calendarWidgetUpdater = calendarWidgetUpdater,
                    refreshPolicy = WorkspaceRefreshPolicy(),
                    refreshClock = WorkspaceRefreshClock(),
                    syncNotifier = WorkspaceSyncNotifier()
                )
            ),
            todoReminderScheduler = reminderScheduler,
            calendarWidgetUpdater = calendarWidgetUpdater
        )
    }

    @Test
    fun initializeForCreateSetsInitialDate() {
        viewModel.initialize(todoId = null, assignedTodoId = null, dueDate = "2026-05-05")

        val state = viewModel.uiState.value
        assertThat(state.isInitialized).isTrue()
        assertThat(state.dueDateInput).isEqualTo("2026-05-05")
    }

    @Test
    fun initializeForEditLoadsTodoState() = runTest {
        val id = repository.addTodo(
            title = "Read doc",
            dueDate = LocalDate.of(2026, 5, 6),
            categoryId = null,
            dueTimeMinutes = 9 * 60,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = null,
            priority = TodoPriority.HIGH
        ).getOrThrow()

        viewModel.initialize(todoId = id, assignedTodoId = null, dueDate = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isInitialized).isTrue()
        assertThat(state.editingTodoId).isEqualTo(id)
        assertThat(state.title).isEqualTo("Read doc")
        assertThat(state.dueDateInput).isEqualTo("2026-05-06")
        assertThat(state.dueTimeInput).isEqualTo("09:00")
        assertThat(state.priority).isEqualTo(TodoPriority.HIGH)
    }

    @Test
    fun initializeWithMissingTodoEmitsExit() = runTest {
        val exitDeferred = CompletableDeferred<TodoEditorSideEffect>()
        backgroundScope.launch {
            exitDeferred.complete(viewModel.sideEffect.first())
        }

        viewModel.initialize(todoId = 999L, assignedTodoId = null, dueDate = null)
        advanceUntilIdle()

        assertThat(exitDeferred.await()).isEqualTo(TodoEditorSideEffect.Exit)
    }

    @Test
    fun saveWithBlankTitleSetsValidationError() = runTest {
        viewModel.initialize(todoId = null, assignedTodoId = null, dueDate = null)
        viewModel.onTitleChange("   ")

        viewModel.onSave()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessageRes).isEqualTo(R.string.todo_error_title_required)
    }

    @Test
    fun saveWithInvalidDateSetsValidationError() = runTest {
        viewModel.initialize(todoId = null, assignedTodoId = null, dueDate = null)
        viewModel.onTitleChange("Task")
        viewModel.onDateInputChange("2026-99-99")

        viewModel.onSave()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessageRes).isEqualTo(R.string.todo_error_due_date_format)
    }

    @Test
    fun saveWithReminderWithoutTimeSetsValidationError() = runTest {
        viewModel.initialize(todoId = null, assignedTodoId = null, dueDate = null)
        viewModel.onTitleChange("Task")
        viewModel.onDateInputChange("2026-05-07")
        viewModel.onReminderEnabledChange(true)

        viewModel.onSave()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessageRes).isEqualTo(R.string.todo_error_reminder_due_time_required)
    }

    @Test
    fun saveCreatesTodoAndEmitsExit() = runTest {
        val exitDeferred = CompletableDeferred<TodoEditorSideEffect>()
        backgroundScope.launch {
            exitDeferred.complete(viewModel.sideEffect.first())
        }

        viewModel.initialize(todoId = null, assignedTodoId = null, dueDate = "2026-05-09")
        viewModel.onTitleChange("Write tests")
        viewModel.onDueTimeInputChange("10:30")
        viewModel.onPrioritySelected(TodoPriority.HIGH)
        viewModel.onSave()
        advanceUntilIdle()

        val todos = repository.observeTodos().first()
        assertThat(todos).hasSize(1)
        assertThat(todos.first().title).isEqualTo("Write tests")
        assertThat(todos.first().priority).isEqualTo(TodoPriority.HIGH)
        assertThat(exitDeferred.await()).isEqualTo(TodoEditorSideEffect.Exit)
        assertThat(reminderScheduler.cancelledTodoIds).contains(todos.first().id)
        assertThat(calendarWidgetUpdater.updateCount).isEqualTo(1)
    }

    @Test
    fun saveEditUpdatesTodoAndSchedulesReminder() = runTest {
        val futureDate = LocalDate.now().plusDays(1)
        val id = repository.addTodo(
            title = "Old",
            dueDate = futureDate,
            categoryId = null,
            dueTimeMinutes = 8 * 60,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = null,
            priority = TodoPriority.LOW
        ).getOrThrow()

        viewModel.initialize(todoId = id, assignedTodoId = null, dueDate = null)
        advanceUntilIdle()
        viewModel.onTitleChange("Updated")
        viewModel.onDueTimeInputChange("09:30")
        viewModel.onReminderEnabledChange(true)
        viewModel.onReminderLeadMinutesChange(10)
        viewModel.onSave()
        advanceUntilIdle()

        val updated = repository.getTodo(id)
        assertThat(updated?.title).isEqualTo("Updated")
        assertThat(updated?.isReminderEnabled).isTrue()
        assertThat(updated?.reminderAtEpochMillis).isNotNull()
        assertThat(reminderScheduler.scheduledTodos.map(TodoItem::id)).contains(id)
        assertThat(calendarWidgetUpdater.updateCount).isEqualTo(1)
    }

    @Test
    fun deleteRemovesTodoAndEmitsExit() = runTest {
        val id = repository.addTodo(
            title = "Delete me",
            dueDate = null,
            categoryId = null,
            dueTimeMinutes = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = null,
            priority = TodoPriority.MEDIUM
        ).getOrThrow()
        val exitDeferred = CompletableDeferred<TodoEditorSideEffect>()
        backgroundScope.launch {
            exitDeferred.complete(viewModel.sideEffect.first())
        }

        viewModel.initialize(todoId = id, assignedTodoId = null, dueDate = null)
        advanceUntilIdle()
        viewModel.onDelete()
        advanceUntilIdle()

        assertThat(repository.getTodo(id)).isNull()
        assertThat(reminderScheduler.cancelledTodoIds).contains(id)
        assertThat(exitDeferred.await()).isEqualTo(TodoEditorSideEffect.Exit)
        assertThat(calendarWidgetUpdater.updateCount).isEqualTo(1)
    }

    @Test
    fun initializeForAssignedEditLoadsReadOnlyTodoState() = runTest {
        assignmentRepository.receivedItems = listOf(
            assignedTodo(
                id = "assigned-edit",
                title = "Shared plan",
                dueDate = LocalDate.of(2026, 5, 11),
                dueTimeMinutes = 10 * 60,
                priority = TodoPriority.HIGH
            )
        )

        viewModel.initialize(todoId = null, assignedTodoId = "assigned-edit", dueDate = null)
        advanceUntilIdle()
        viewModel.onTitleChange("Should be ignored")
        viewModel.onDateInputChange("2026-05-12")
        viewModel.onDueTimeInputChange("12:00")
        viewModel.onPrioritySelected(TodoPriority.LOW)

        val state = viewModel.uiState.value
        assertThat(state.isAssignedEdit).isTrue()
        assertThat(state.showDelete).isFalse()
        assertThat(state.sheetTitleRes).isEqualTo(R.string.todo_editor_title_received_task)
        assertThat(state.title).isEqualTo("Shared plan")
        assertThat(state.dueDateInput).isEqualTo("2026-05-11")
        assertThat(state.dueTimeInput).isEqualTo("10:00")
        assertThat(state.priority).isEqualTo(TodoPriority.HIGH)
    }

    @Test
    fun saveAssignedEditUpsertsReminderAndExits() = runTest {
        val dueDate = LocalDate.now().plusDays(1)
        val exitDeferred = CompletableDeferred<TodoEditorSideEffect>()
        assignmentRepository.receivedItems = listOf(
            assignedTodo(
                id = "assigned-reminder",
                dueDate = dueDate,
                dueTimeMinutes = 18 * 60
            )
        )
        backgroundScope.launch {
            exitDeferred.complete(viewModel.sideEffect.first())
        }

        viewModel.initialize(todoId = null, assignedTodoId = "assigned-reminder", dueDate = null)
        advanceUntilIdle()
        viewModel.onReminderEnabledChange(true)
        viewModel.onReminderLeadMinutesChange(30)
        viewModel.onSave()
        advanceUntilIdle()

        assertThat(assignmentRepository.upsertedReminder?.assignedTodoId).isEqualTo("assigned-reminder")
        assertThat(assignmentRepository.upsertedReminder?.enabled).isTrue()
        assertThat(exitDeferred.await()).isEqualTo(TodoEditorSideEffect.Exit)
        assertThat(calendarWidgetUpdater.updateCount).isEqualTo(1)
    }

    private class FakeAssignmentRepository : AssignmentRepository {
        var receivedItems: List<AssignedTodo> = emptyList()
        var upsertedReminder: UpsertedReminder? = null

        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>,
            assignmentMode: com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> = Result.success(emptyList())

        override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.success(receivedItems)

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.success(emptyList())

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            MutableStateFlow(receivedItems)

        override fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            MutableStateFlow(emptyList())

        override fun observeFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Flow<List<AssignedTodo>> = MutableStateFlow(emptyList())

        override suspend fun decideBundleItems(
            bundleId: String,
            decisions: Map<String, AssignmentDecision>
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun upsertAssignedTodoReminder(
            assignedTodoId: String,
            reminderAt: String,
            enabled: Boolean
        ): Result<Unit> {
            upsertedReminder = UpsertedReminder(
                assignedTodoId = assignedTodoId,
                reminderAt = reminderAt,
                enabled = enabled
            )
            return Result.success(Unit)
        }

        override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeFriendRepository : FriendRepository {
        override suspend fun getFriends(): Result<List<Friend>> = Result.success(emptyList())

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> =
            Result.success(emptyList())

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> =
            Result.success(emptyList())

        override suspend fun sendRequest(nickname: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun acceptRequest(requestId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun declineRequest(requestId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun removeFriend(friendshipId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }

    private data class UpsertedReminder(
        val assignedTodoId: String,
        val reminderAt: String,
        val enabled: Boolean
    )

    private class RecordingReminderScheduler : TodoReminderScheduler {
        val scheduledTodos = mutableListOf<TodoItem>()
        val cancelledTodoIds = mutableListOf<Long>()

        override suspend fun schedule(todo: TodoItem) {
            scheduledTodos += todo
        }

        override suspend fun cancel(todoId: Long) {
            cancelledTodoIds += todoId
        }

        override suspend fun rescheduleAll() = Unit
    }

    private class RecordingCalendarWidgetUpdater : CalendarWidgetUpdater {
        var updateCount: Int = 0
            private set

        override suspend fun updateCalendarWidgets(): Result<Unit> {
            updateCount += 1
            return Result.success(Unit)
        }
    }
}

private fun assignedTodo(
    id: String = "assigned-1",
    title: String = "Shared todo",
    dueDate: LocalDate? = null,
    dueTimeMinutes: Int? = null,
    priority: TodoPriority = TodoPriority.MEDIUM,
    reminder: AssignedTodoReminder? = null
) = AssignedTodo(
    id = id,
    bundleId = "bundle-1",
    title = title,
    description = null,
    dueDate = dueDate,
    dueTimeMinutes = dueTimeMinutes,
    priority = priority,
    category = null,
    status = AssignedTodoStatus.ACCEPTED,
    terminalReason = null,
    progressPercent = 0,
    sender = AssignedTodoUser(id = "friend-1", nickname = "monday"),
    receiver = AssignedTodoUser(id = "me", nickname = "tester"),
    reminder = reminder,
    checklist = emptyList()
)
