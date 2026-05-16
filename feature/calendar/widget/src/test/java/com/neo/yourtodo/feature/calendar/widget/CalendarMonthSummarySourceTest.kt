package com.neo.yourtodo.feature.calendar.widget

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodoSummariesUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveTaskSurfaceSummariesUseCase
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CalendarMonthSummarySourceTest {

    @Test
    fun summariesFor_includesVisibleReceivedAssignedTodos() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val source = source(
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-1",
                    title = "From friend",
                    dueDate = targetDate
                )
            )
        )

        val summaries = source.summariesFor(YearMonth.of(2026, 5))

        val summary = summaries.getValue(targetDate)
        assertThat(summary.indicatorCount).isEqualTo(1)
        assertThat(summary.todos.map { it.title }).containsExactly("From friend")
    }

    @Test
    fun summariesFor_includesDirectAssignedTodos() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val source = source(
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-direct",
                    title = "Direct from friend",
                    dueDate = targetDate,
                    assignmentMode = AssignmentMode.DIRECT
                )
            )
        )

        val summaries = source.summariesFor(YearMonth.of(2026, 5))

        assertThat(summaries.getValue(targetDate).todos.map { it.title })
            .containsExactly("Direct from friend")
    }

    @Test
    fun summariesFor_excludesPendingAssignedTodos() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val source = source(
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-pending",
                    title = "Pending request",
                    dueDate = targetDate,
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE
                )
            )
        )

        val summaries = source.summariesFor(YearMonth.of(2026, 5))

        assertThat(summaries).doesNotContainKey(targetDate)
    }

    @Test
    fun summariesFor_includesCompletedReceivedAssignedTodosFromHistoryFeed() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val source = source(
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-done",
                    title = "Done from friend",
                    dueDate = targetDate,
                    status = AssignedTodoStatus.DONE
                )
            )
        )

        val summaries = source.summariesFor(YearMonth.of(2026, 5))

        val summary = summaries.getValue(targetDate)
        assertThat(summary.todos.map { it.title }).containsExactly("Done from friend")
        assertThat(summary.todos.single().isDone).isTrue()
    }

    private fun source(
        assignedTodos: List<AssignedTodo>
    ): CalendarMonthSummarySource {
        val getAssignedTodosUseCase = GetAssignedTodosUseCase(
            FakeAssignmentRepository(assignedTodos)
        )
        return DomainCalendarMonthSummarySource(
            ObserveTaskSurfaceSummariesUseCase(
                observeMonthlyTodoSummariesUseCase = ObserveMonthlyTodoSummariesUseCase(
                    observeMonthlyTodosUseCase = ObserveMonthlyTodosUseCase(EmptyTodoRepository())
                ),
                getAssignedTodosUseCase = getAssignedTodosUseCase
            )
        )
    }

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

    private class EmptyTodoRepository : TodoItemRepository {
        override fun observeTodos(): Flow<List<TodoItem>> = flowOf(emptyList())

        override fun observeTodosByDueDateRange(
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<List<TodoItem>> = flowOf(emptyList())

        override suspend fun getTodo(id: Long): TodoItem? = null

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
        ): Result<Long> = error("unused")

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
        ): Result<Unit> = error("unused")

        override suspend fun deleteTodo(id: Long): Result<Unit> = error("unused")

        override suspend fun toggleTodoDone(id: Long): Result<Unit> = error("unused")

        override suspend fun syncTodos(): Result<Unit> = error("unused")
    }
}
