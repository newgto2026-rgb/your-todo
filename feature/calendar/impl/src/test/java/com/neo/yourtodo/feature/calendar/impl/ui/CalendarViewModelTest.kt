package com.neo.yourtodo.feature.calendar.impl.ui

import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ManageAssignedTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodoSummariesUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodosUseCase
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.domain.usecase.ToggleTodoDoneUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
import androidx.lifecycle.SavedStateHandle
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uiStateCollectionJobs = mutableListOf<Job>()

    @After
    fun tearDown() {
        uiStateCollectionJobs.forEach { job -> job.cancel() }
        uiStateCollectionJobs.clear()
    }

    @Test
    fun profileInitialComesFromAuthSessionNickname() = runTest {
        val authRepository = FakeAuthRepository()
        val viewModel = createViewModel(
            repository = FakeTodoRepository(),
            authRepository = authRepository
        )

        authRepository.authSession.value = testAuthSession(nickname = "taeyunlive")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.profileInitial).isEqualTo("taeyunlive")
    }

    @Test
    fun initialState_hasCurrentMonthAndSelectedDate() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())

        val state = viewModel.uiState.value
        val today = LocalDate.now()

        assertThat(state.currentMonth).isEqualTo(java.time.YearMonth.from(today))
        assertThat(state.selectedDate).isEqualTo(today)
    }

    @Test
    fun nextMonthAction_movesMonthAndKeepsDayInRange() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())
        val before = viewModel.uiState.value

        viewModel.onAction(CalendarAction.OnNextMonthClick)
        advanceUntilIdle()

        val after = viewModel.uiState.value
        val expectedMonth = before.currentMonth.plusMonths(1)
        val expectedDay = min(before.selectedDate.dayOfMonth, expectedMonth.lengthOfMonth())

        assertThat(after.currentMonth).isEqualTo(expectedMonth)
        assertThat(after.selectedDate).isEqualTo(expectedMonth.atDay(expectedDay))
    }

    @Test
    fun dateClickAction_updatesSelectedDate() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())
        val targetDate = viewModel.uiState.value.currentMonth.atDay(15)

        viewModel.onAction(CalendarAction.OnDateClick(targetDate))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(targetDate)
    }

    @Test
    fun selectRouteDate_updatesMonthAndSelectedDate() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())
        val targetDate = LocalDate.of(2026, 5, 7)

        viewModel.selectRouteDate(targetDate.toString())
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentMonth).isEqualTo(YearMonth.of(2026, 5))
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(targetDate)
    }

    @Test
    fun selectRouteDate_forDifferentMonthUpdatesVisibleMonthAndSelectedDate() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())
        val targetDate = viewModel.uiState.value.currentMonth.plusMonths(2).atDay(11)

        viewModel.selectRouteDate(targetDate.toString())
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentMonth).isEqualTo(YearMonth.from(targetDate))
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(targetDate)
        assertThat(viewModel.uiState.value.days.map { it.date }).contains(targetDate)
    }

    @Test
    fun selectRouteDate_withInvalidDateFallsBackToToday() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())
        val today = LocalDate.now()

        viewModel.selectRouteDate("not-a-date")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentMonth).isEqualTo(YearMonth.from(today))
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(today)
    }

    @Test
    fun todoClickAction_emitsNavigateSideEffect() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val targetDate = viewModel.uiState.value.currentMonth.atDay(10)

        val todoId = repository.addTodo(
            title = "In month",
            dueDate = targetDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        ).getOrThrow()

        advanceUntilIdle()
        viewModel.onAction(CalendarAction.OnDateClick(targetDate))
        advanceUntilIdle()

        val emitted = async { viewModel.sideEffect.first() }
        viewModel.onAction(CalendarAction.OnTodoClick(todoId))
        advanceUntilIdle()

        assertThat(emitted.await()).isEqualTo(CalendarSideEffect.NavigateToTodoEdit(todoId))
    }

    @Test
    fun assignedTodoClickAction_emitsAssignedEditSideEffect() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())
        val emitted = async { viewModel.sideEffect.first() }

        viewModel.onAction(CalendarAction.OnTodoClick(todoId = -1L, assignedTodoId = "assigned-calendar"))
        advanceUntilIdle()

        assertThat(emitted.await())
            .isEqualTo(CalendarSideEffect.NavigateToAssignedTodoEdit("assigned-calendar"))
    }

    @Test
    fun addTodoClickAction_emitsSelectedDateAddSideEffect() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val targetDate = viewModel.uiState.value.currentMonth.atDay(12)

        viewModel.onAction(CalendarAction.OnDateClick(targetDate))
        advanceUntilIdle()

        val emitted = async { viewModel.sideEffect.first() }
        viewModel.onAction(CalendarAction.OnAddTodoClick)
        advanceUntilIdle()

        assertThat(emitted.await()).isEqualTo(CalendarSideEffect.NavigateToTodoAdd(targetDate))
    }

    @Test
    fun toggleTodoDoneAction_marksSelectedDateTodoDone() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val targetDate = viewModel.uiState.value.currentMonth.atDay(13)
        val todoId = repository.addTodo(
            title = "Toggle from calendar",
            dueDate = targetDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        ).getOrThrow()

        viewModel.onAction(CalendarAction.OnDateClick(targetDate))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.selectedDateTodos.single().isDone).isFalse()

        viewModel.onAction(CalendarAction.OnToggleTodoDone(todoId))
        advanceUntilIdle()

        assertThat(repository.getTodo(todoId)?.isDone).isTrue()
        assertThat(viewModel.uiState.value.selectedDateTodos.single().isDone).isTrue()
    }

    @Test
    fun workspaceSyncSnapshotUpdatesAssignedTodos() = runTest {
        val repository = FakeTodoRepository()
        val workspaceSyncNotifier = WorkspaceSyncNotifier()
        val assignmentRepository = FakeAssignmentRepository(
            receivedItems = listOf(
                assignedTodo(
                    id = "assigned-sync",
                    title = "Synced shared",
                    dueDate = LocalDate.now()
                )
            )
        )
        val viewModel = createViewModel(
            repository = repository,
            assignmentRepository = assignmentRepository,
            workspaceSyncNotifier = workspaceSyncNotifier
        )

        RefreshWorkspaceUseCase(
            todoRepository = repository,
            friendRepository = FakeFriendRepository(),
            assignmentRepository = assignmentRepository,
            syncNotifier = workspaceSyncNotifier
        )()
        advanceUntilIdle()

        assertThat(repository.syncCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.selectedDateTodos.map { it.assignedTodoId })
            .contains("assigned-sync")
    }


    @Test
    fun selectedDateTodos_includeOnlySelectedDateTodos() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val currentMonth = viewModel.uiState.value.currentMonth
        val selectedDate = currentMonth.atDay(10)
        val anotherDate = currentMonth.atDay(11)

        repository.addTodo(
            title = "Selected date todo",
            dueDate = selectedDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        )
        repository.addTodo(
            title = "Another date todo",
            dueDate = anotherDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        )
        advanceUntilIdle()

        viewModel.onAction(CalendarAction.OnDateClick(selectedDate))
        advanceUntilIdle()

        val todos = viewModel.uiState.value.selectedDateTodos
        assertThat(todos).hasSize(1)
        assertThat(todos.first().title).isEqualTo("Selected date todo")
    }

    @Test
    fun summaries_includeOnlyCurrentMonthTodos() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val currentMonth = viewModel.uiState.value.currentMonth
        val inMonthDate = currentMonth.atDay(10)
        val outOfMonthDate = currentMonth.plusMonths(1).atDay(10)

        repository.addTodo(
            title = "In month",
            dueDate = inMonthDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        )
        repository.addTodo(
            title = "Out of month",
            dueDate = outOfMonthDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.summariesByDate.keys).contains(inMonthDate)
        assertThat(state.summariesByDate.keys).doesNotContain(outOfMonthDate)
        assertThat(state.summariesByDate[inMonthDate]?.indicatorCount).isEqualTo(1)
    }

    @Test
    fun selectedDateTodos_mapReminderFields() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val selectedDate = viewModel.uiState.value.currentMonth.atDay(8)

        repository.addTodo(
            title = "Reminder mapped",
            dueDate = selectedDate,
            categoryId = null,
            dueTimeMinutes = 9 * 60 + 30,
            reminderAtEpochMillis = 0L,
            isReminderEnabled = true,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = 10
        )
        advanceUntilIdle()

        viewModel.onAction(CalendarAction.OnDateClick(selectedDate))
        advanceUntilIdle()

        val todo = viewModel.uiState.value.selectedDateTodos.single()
        assertThat(todo.isReminderEnabled).isTrue()
        assertThat(todo.reminderLeadMinutes).isEqualTo(10)
        assertThat(todo.dueTimeLabel).isNotNull()
    }

    @Test
    fun selectedDateTodos_includeReceivedAssignedTodos() = runTest {
        val repository = FakeTodoRepository()
        val assignmentRepository = FakeAssignmentRepository(
            receivedItems = listOf(
                assignedTodo(
                    id = "assigned-calendar",
                    title = "From friend",
                    dueDate = YearMonth.now().atDay(9)
                )
            )
        )
        val viewModel = createViewModel(
            repository = repository,
            assignmentRepository = assignmentRepository
        )
        val selectedDate = YearMonth.now().atDay(9)

        viewModel.onAction(CalendarAction.OnDateClick(selectedDate))
        advanceUntilIdle()

        val todo = viewModel.uiState.value.selectedDateTodos.single()
        assertThat(todo.title).isEqualTo("From friend")
        assertThat(todo.sourceLabel).isEqualTo("@monday")
        assertThat(viewModel.uiState.value.summariesByDate[selectedDate]?.indicatorCount).isEqualTo(1)
    }

    @Test
    fun completedReceivedAssignedTodoStaysOnSelectedDate() = runTest {
        val selectedDate = YearMonth.now().atDay(9)
        val assignmentRepository = FakeAssignmentRepository(
            receivedItems = listOf(
                assignedTodo(
                    id = "assigned-calendar-done",
                    title = "Finish review",
                    dueDate = selectedDate
                )
            )
        )
        val viewModel = createViewModel(
            repository = FakeTodoRepository(),
            assignmentRepository = assignmentRepository
        )

        viewModel.onAction(CalendarAction.OnDateClick(selectedDate))
        advanceUntilIdle()
        viewModel.onAction(
            CalendarAction.OnToggleTodoDone(
                todoId = viewModel.uiState.value.selectedDateTodos.single().id,
                assignedTodoId = "assigned-calendar-done"
            )
        )
        advanceUntilIdle()

        val todo = viewModel.uiState.value.selectedDateTodos.single()
        assertThat(todo.title).isEqualTo("Finish review")
        assertThat(todo.isDone).isTrue()
    }

    @Test
    fun buildMonthCells_includesAdjacentMonthDatesWithoutNullCells() {
        val yearMonth = YearMonth.of(2026, 4)
        val selectedDate = yearMonth.atDay(9)
        val today = selectedDate

        val cells = buildMonthCells(
            yearMonth = yearMonth,
            selectedDate = selectedDate,
            today = today,
            summariesByDate = emptyMap()
        )

        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val leadingBlanks = yearMonth.atDay(1).dayOfWeek.distanceFrom(firstDayOfWeek)
        val expectedFirstDate = yearMonth.atDay(1).minusDays(leadingBlanks.toLong())

        assertThat(cells).isNotEmpty()
        assertThat(cells.all { it.date != null }).isTrue()
        assertThat(cells.first().date).isEqualTo(expectedFirstDate)
        assertThat(cells.any { !it.isCurrentMonth }).isTrue()
    }

    @Test
    fun todayTaskCount_isExposedByUiState() {
        val today = LocalDate.of(2026, 4, 9)
        val uiState = CalendarUiState(
            currentMonth = YearMonth.of(2026, 4),
            selectedDate = today,
            days = emptyList(),
            summariesByDate = mapOf(
                today to DateTodoSummary(
                    date = today,
                    todos = emptyList(),
                    indicatorCount = 3,
                    overflowCount = 2
                )
            ),
            todayTaskCount = 5,
            selectedDateTodos = emptyList()
        )

        assertThat(uiState.todayTaskCount).isEqualTo(5)
    }

    private fun createViewModel(
        repository: FakeTodoRepository,
        authRepository: FakeAuthRepository = FakeAuthRepository(),
        assignmentRepository: FakeAssignmentRepository = FakeAssignmentRepository(),
        workspaceSyncNotifier: WorkspaceSyncNotifier = WorkspaceSyncNotifier()
    ): CalendarViewModel {
        val viewModel = CalendarViewModel(
            savedStateHandle = SavedStateHandle(),
            observeAuthSessionUseCase = ObserveAuthSessionUseCase(authRepository),
            observeMonthlyTodoSummariesUseCase = ObserveMonthlyTodoSummariesUseCase(
                observeMonthlyTodosUseCase = ObserveMonthlyTodosUseCase(repository)
            ),
            observeMonthlyTodosUseCase = ObserveMonthlyTodosUseCase(repository),
            toggleTodoDoneUseCase = ToggleTodoDoneUseCase(repository),
            getAssignedTodosUseCase = GetAssignedTodosUseCase(assignmentRepository),
            manageAssignedTodoUseCase = ManageAssignedTodoUseCase(assignmentRepository),
            workspaceSyncNotifier = workspaceSyncNotifier
        )
        uiStateCollectionJobs += CoroutineScope(mainDispatcherRule.testDispatcher).launch {
            viewModel.uiState.collect()
        }
        return viewModel
    }

    private fun testAuthSession(nickname: String): AuthSession =
        AuthSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            user = AuthUser(
                id = "user-id",
                nickname = nickname,
                email = "user@example.com",
                onboardingRequired = false
            )
        )

    private class FakeAuthRepository : AuthRepository {
        override val authSession = MutableStateFlow<AuthSession?>(null)

        override suspend fun signInWithGoogle(idToken: String): Result<AuthSession> =
            Result.failure(UnsupportedOperationException())

        override suspend fun completeNicknameOnboarding(nickname: String): Result<AuthSession> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signOut() = Unit
    }

    private class FakeFriendRepository : FriendRepository {
        override suspend fun getFriends(): Result<List<Friend>> = Result.success(emptyList())

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> =
            Result.success(emptyList())

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> =
            Result.success(emptyList())

        override suspend fun sendRequest(nickname: String): Result<Unit> = Result.success(Unit)

        override suspend fun acceptRequest(requestId: String): Result<Unit> = Result.success(Unit)

        override suspend fun declineRequest(requestId: String): Result<Unit> = Result.success(Unit)

        override suspend fun removeFriend(friendshipId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeAssignmentRepository(
        var receivedItems: List<AssignedTodo> = emptyList()
    ) : AssignmentRepository {
        var completedAssignedTodoId: String? = null

        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>
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

        override suspend fun decideBundleItems(
            bundleId: String,
            decisions: Map<String, AssignmentDecision>
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> {
            completedAssignedTodoId = assignedTodoId
            receivedItems = receivedItems.map { item ->
                if (item.id == assignedTodoId) {
                    item.copy(status = AssignedTodoStatus.DONE, progressPercent = 100)
                } else {
                    item
                }
            }
            return Result.success(receivedItems.firstOrNull { it.id == assignedTodoId } ?: assignedTodo())
        }

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun upsertAssignedTodoReminder(
            assignedTodoId: String,
            reminderAt: String,
            enabled: Boolean
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }
}

private fun assignedTodo(
    id: String = "assigned-1",
    title: String = "Shared todo",
    dueDate: LocalDate? = null,
    status: AssignedTodoStatus = AssignedTodoStatus.ACCEPTED
) = AssignedTodo(
    id = id,
    bundleId = "bundle-1",
    title = title,
    description = null,
    dueDate = dueDate,
    priority = TodoPriority.MEDIUM,
    category = null,
    status = status,
    terminalReason = null,
    progressPercent = if (status == AssignedTodoStatus.DONE) 100 else 0,
    sender = AssignedTodoUser(id = "friend-1", nickname = "monday"),
    receiver = AssignedTodoUser(id = "me", nickname = "tester"),
    reminder = null,
    checklist = emptyList()
)
