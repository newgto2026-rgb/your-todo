package com.neo.yourtodo.feature.calendar.widget

import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CalendarMonthWidgetPresenterTest {

    @Test
    fun present_mapsMonthlySummariesToWidgetDays() = runTest {
        val targetDate = LocalDate.of(2026, 5, 7)
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = emptyList(),
                        indicatorCount = 2,
                        overflowCount = 1
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(state.monthLabel).isEqualTo("2026 May")
        assertThat(day.isToday).isTrue()
        assertThat(day.isCurrentMonth).isTrue()
        assertThat(day.taskCountLabel).isEqualTo("3")
    }

    @Test
    fun present_capsLargeTaskCounts() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = emptyList(),
                        indicatorCount = 9,
                        overflowCount = 4
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.taskCountLabel).isEqualTo("9+")
    }

    @Test
    fun present_mapsUpToFourTodosToExpandedPreviewChips() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = listOf(
                            todo(title = "Morning review", createdAt = 1L),
                            todo(title = "Project update", createdAt = 2L),
                            todo(title = "Dinner", createdAt = 3L),
                            todo(title = "Read notes", createdAt = 4L)
                        ),
                        indicatorCount = 4,
                        overflowCount = 0
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.todoChips.map { it.label })
            .containsExactly("Read notes", "Dinner", "Project update", "Morning review")
            .inOrder()
        assertThat(day.todoChips).hasSize(4)
        assertThat(day.todoChips.none { it.isOverflow }).isTrue()
    }

    @Test
    fun present_mapsFiveOrMoreTodosToThreePreviewChipsAndOverflow() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = listOf(
                            todo(title = "A", createdAt = 1L),
                            todo(title = "B", createdAt = 2L),
                            todo(title = "C", createdAt = 3L),
                            todo(title = "D", createdAt = 4L),
                            todo(title = "E", createdAt = 5L)
                        ),
                        indicatorCount = 4,
                        overflowCount = 1
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.todoChips.map { it.label })
            .containsExactly("E", "D", "C", "+2")
            .inOrder()
        assertThat(day.todoChips.last().isOverflow).isTrue()
    }

    @Test
    fun present_ordersPreviewChipsByDoneTimePriorityAndCreatedAt() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = listOf(
                            todo(title = "Done early", isDone = true, dueTimeMinutes = 540, priority = TodoPriority.HIGH),
                            todo(title = "No time high", dueTimeMinutes = null, priority = TodoPriority.HIGH),
                            todo(title = "Early low", dueTimeMinutes = 540, priority = TodoPriority.LOW),
                            todo(title = "Early high", dueTimeMinutes = 540, priority = TodoPriority.HIGH),
                            todo(title = "Later high", dueTimeMinutes = 600, priority = TodoPriority.HIGH)
                        ),
                        indicatorCount = 4,
                        overflowCount = 1
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.todoChips.map { it.label })
            .containsExactly("Early high", "Early low", "Later high", "+2")
            .inOrder()
    }

    @Test
    fun present_usesDisplayedMonthWhenProvided() = runTest {
        val displayedMonth = YearMonth.of(2026, 7)
        val targetDate = LocalDate.of(2026, 7, 11)
        val summarySource = FakeCalendarMonthSummarySource(
            summaries = mapOf(
                targetDate to DateTodoSummary(
                    date = targetDate,
                    todos = emptyList(),
                    indicatorCount = 1,
                    overflowCount = 1
                )
            )
        )
        val presenter = presenter(
            summarySource = summarySource,
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(locale = Locale.US, displayedMonth = displayedMonth)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(summarySource.requestedMonths).containsExactly(displayedMonth)
        assertThat(state.monthLabel).isEqualTo("2026 July")
        assertThat(day.isToday).isFalse()
        assertThat(day.isCurrentMonth).isTrue()
        assertThat(day.taskCountLabel).isEqualTo("2")
    }

    @Test
    fun present_returnsErrorStateWhenSourceFails() = runTest {
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(error = IllegalStateException("boom")),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)

        assertThat(state.isError).isTrue()
        assertThat(state.weeks).isEmpty()
    }

    @Test
    fun present_includesVisibleReceivedAssignedTodos() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(),
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-1",
                    title = "From friend",
                    dueDate = targetDate
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.taskCountLabel).isEqualTo("1")
        assertThat(day.todoChips.map { it.label }).containsExactly("From friend")
    }

    @Test
    fun present_includesDirectAssignedTodos() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(),
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-direct",
                    title = "Direct from friend",
                    dueDate = targetDate,
                    assignmentMode = AssignmentMode.DIRECT
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.taskCountLabel).isEqualTo("1")
        assertThat(day.todoChips.map { it.label }).containsExactly("Direct from friend")
    }

    @Test
    fun present_excludesPendingAssignedTodos() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(),
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-pending",
                    title = "Pending request",
                    dueDate = targetDate,
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.taskCountLabel).isNull()
        assertThat(day.todoChips).isEmpty()
    }

    @Test
    fun present_includesCompletedReceivedAssignedTodosFromHistoryFeed() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = presenter(
            summarySource = FakeCalendarMonthSummarySource(),
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-done",
                    title = "Done from friend",
                    dueDate = targetDate,
                    status = AssignedTodoStatus.DONE
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.taskCountLabel).isEqualTo("1")
        assertThat(day.todoChips.map { it.label }).containsExactly("Done from friend")
        assertThat(day.todoChips.single().isDone).isTrue()
    }

    private fun presenter(
        summarySource: CalendarMonthSummarySource,
        assignedTodos: List<AssignedTodo> = emptyList(),
        clock: Clock
    ) = CalendarMonthWidgetPresenter(
        summarySource = summarySource,
        getAssignedTodosUseCase = GetAssignedTodosUseCase(
            FakeAssignmentRepository(assignedTodos)
        ),
        clock = clock
    )

    private class FakeCalendarMonthSummarySource(
        private val summaries: Map<LocalDate, DateTodoSummary> = emptyMap(),
        private val error: Throwable? = null
    ) : CalendarMonthSummarySource {
        val requestedMonths: MutableList<YearMonth> = mutableListOf()

        override suspend fun summariesFor(yearMonth: YearMonth): Map<LocalDate, DateTodoSummary> {
            requestedMonths += yearMonth
            error?.let { throw it }
            return summaries
        }
    }

    private fun fixedClock(instant: String): Clock =
        Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"))

    private fun todo(
        title: String,
        isDone: Boolean = false,
        dueTimeMinutes: Int? = null,
        priority: TodoPriority = TodoPriority.MEDIUM,
        createdAt: Long = 0L
    ): TodoSummary =
        TodoSummary(
            id = createdAt,
            title = title,
            isDone = isDone,
            dueTimeMinutes = dueTimeMinutes,
            priority = priority,
            createdAt = createdAt
        )

    private fun assignedTodo(
        id: String,
        title: String,
        dueDate: LocalDate,
        status: AssignedTodoStatus = AssignedTodoStatus.ACCEPTED,
        assignmentMode: AssignmentMode = AssignmentMode.REQUEST
    ): AssignedTodo =
        AssignedTodo(
            id = id,
            bundleId = "bundle-$id",
            title = title,
            description = null,
            dueDate = dueDate,
            dueTimeMinutes = null,
            priority = TodoPriority.MEDIUM,
            category = null,
            status = status,
            terminalReason = null,
            progressPercent = if (status == AssignedTodoStatus.DONE) 100 else 0,
            sender = null,
            receiver = null,
            assignmentMode = assignmentMode,
            reminder = null,
            createdAt = Instant.parse("2026-05-07T00:00:00Z"),
            completedAt = null
        )

    private class FakeAssignmentRepository(
        private val assignedTodos: List<AssignedTodo>
    ) : AssignmentRepository {
        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>,
            assignmentMode: AssignmentMode
        ): Result<AssignmentBundle> = error("unused")

        override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
            error("unused")

        override suspend fun getFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> = error("unused")

        override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.success(
                when (status) {
                    AssignmentFeedStatus.ACTIVE -> assignedTodos.filter { it.status != AssignedTodoStatus.DONE }
                    AssignmentFeedStatus.HISTORY -> assignedTodos.filter { it.status == AssignedTodoStatus.DONE }
                    AssignmentFeedStatus.PENDING -> emptyList()
                }
            )

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            error("unused")

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            flowOf(
                when (status) {
                    AssignmentFeedStatus.ACTIVE -> assignedTodos.filter { it.status != AssignedTodoStatus.DONE }
                    AssignmentFeedStatus.HISTORY -> assignedTodos.filter { it.status == AssignedTodoStatus.DONE }
                    AssignmentFeedStatus.PENDING -> emptyList()
                }
            )

        override fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            flowOf(emptyList())

        override fun observeFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Flow<List<AssignedTodo>> = flowOf(emptyList())

        override suspend fun decideBundleItems(
            bundleId: String,
            decisions: Map<String, AssignmentDecision>
        ): Result<AssignmentBundle> = error("unused")

        override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            error("unused")

        override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            error("unused")

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            error("unused")

        override suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit> =
            error("unused")

        override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            error("unused")

        override suspend fun upsertAssignedTodoReminder(
            assignedTodoId: String,
            reminderAt: String,
            enabled: Boolean
        ): Result<Unit> = error("unused")

        override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
            error("unused")
    }
}
