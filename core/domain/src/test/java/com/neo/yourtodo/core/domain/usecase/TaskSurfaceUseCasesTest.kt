package com.neo.yourtodo.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import com.neo.yourtodo.core.model.TodoSummary
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoUser
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TaskSurfaceUseCasesTest {

    @Test
    fun buildTaskSurfaceListAppliesTodayPrioritySortAndAssignedOverrides() {
        val today = LocalDate.of(2026, 5, 16)
        val localOverdue = todo(
            id = 1,
            title = "Local overdue",
            dueDate = today.minusDays(1),
            priority = TodoPriority.LOW
        )
        val localTodayDone = todo(
            id = 2,
            title = "Done today",
            dueDate = today,
            isDone = true,
            priority = TodoPriority.HIGH
        )
        val assignedToday = assignedTodo(
            id = "assigned-today",
            title = "Assigned today",
            dueDate = today,
            priority = TodoPriority.HIGH,
            dueTimeMinutes = 8 * 60
        )
        val assignedDone = assignedTodo(
            id = "assigned-done",
            title = "Assigned done",
            dueDate = today,
            status = AssignedTodoStatus.DONE,
            priority = TodoPriority.MEDIUM,
            dueTimeMinutes = 9 * 60
        )

        val surface = BuildTaskSurfaceListUseCase()(
            localTodos = listOf(localOverdue, localTodayDone),
            assignedTodos = listOf(assignedToday, assignedDone),
            selectedFilter = TodoFilter.TODAY,
            selectedPriorityFilter = TodoPriorityFilter.ALL,
            selectedSortOption = TodoSortOption.DUE_DATE,
            today = today,
            assignedOverrides = AssignedTaskSurfaceOverrides(
                activeIds = setOf("assigned-done")
            )
        )

        assertThat(surface.items.map { it.title })
            .containsExactly("Assigned today", "Assigned done", "Local overdue")
            .inOrder()
        assertThat(surface.completedLocalTodoIds).containsExactly(2L)
        assertThat(surface.completedAssignedTodoIds).isEmpty()
    }

    @Test
    fun buildTaskSurfaceListGroupsFriendAndSelfSectionsWithCompletedLast() {
        val today = LocalDate.of(2026, 5, 16)
        val surface = BuildTaskSurfaceListUseCase()(
            localTodos = listOf(
                todo(id = 1, title = "My task", dueDate = null, priority = TodoPriority.HIGH)
            ),
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-bob",
                    title = "Bob task",
                    senderNickname = "Bob",
                    priority = TodoPriority.MEDIUM
                ),
                assignedTodo(
                    id = "assigned-ann",
                    title = "Ann task",
                    senderNickname = "Ann",
                    priority = TodoPriority.LOW
                ),
                assignedTodo(
                    id = "assigned-done",
                    title = "Done friend task",
                    senderNickname = "Ann",
                    status = AssignedTodoStatus.DONE,
                    priority = TodoPriority.HIGH
                )
            ),
            selectedFilter = TodoFilter.ALL,
            selectedPriorityFilter = TodoPriorityFilter.ALL,
            selectedSortOption = TodoSortOption.FRIEND,
            today = today
        )

        assertThat(surface.sections.map { it.key }).containsExactly(
            TaskSurfaceSectionKey.Friend("Ann"),
            TaskSurfaceSectionKey.Friend("Bob"),
            TaskSurfaceSectionKey.Self,
            TaskSurfaceSectionKey.Completed
        ).inOrder()
        assertThat(surface.items.map { it.title }).containsExactly(
            "Ann task",
            "Bob task",
            "My task",
            "Done friend task"
        ).inOrder()
    }

    @Test
    fun buildTaskSurfaceDateTodosFiltersSelectedDateAndKeepsLocalThenAssignedOrder() {
        val selectedDate = LocalDate.of(2026, 5, 16)
        val items = BuildTaskSurfaceDateTodosUseCase()(
            selectedDate = selectedDate,
            localTodos = listOf(
                todo(id = 3, title = "Done high", dueDate = selectedDate, isDone = true, priority = TodoPriority.HIGH),
                todo(id = 2, title = "Selected low", dueDate = selectedDate, priority = TodoPriority.LOW),
                todo(id = 1, title = "Selected high", dueDate = selectedDate, priority = TodoPriority.HIGH),
                todo(id = 4, title = "Other date", dueDate = selectedDate.plusDays(1), priority = TodoPriority.HIGH)
            ),
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-low",
                    title = "Assigned low",
                    dueDate = selectedDate,
                    priority = TodoPriority.LOW,
                    dueTimeMinutes = 9 * 60
                ),
                assignedTodo(
                    id = "assigned-high",
                    title = "Assigned high",
                    dueDate = selectedDate,
                    priority = TodoPriority.HIGH,
                    dueTimeMinutes = 9 * 60
                ),
                assignedTodo(
                    id = "assigned-other-date",
                    title = "Assigned other date",
                    dueDate = selectedDate.plusDays(1),
                    priority = TodoPriority.HIGH
                )
            )
        )

        assertThat(items.map { it.title }).containsExactly(
            "Selected high",
            "Selected low",
            "Done high",
            "Assigned high",
            "Assigned low"
        ).inOrder()
        assertThat(items.take(3).all { it.assignedTodoId == null }).isTrue()
    }

    @Test
    fun mergeTaskSurfaceSummariesAddsAssignedTodosOnlyForTargetMonthAndRecomputesOverflow() {
        val date = LocalDate.of(2026, 5, 16)
        val outOfMonthDate = LocalDate.of(2026, 6, 1)
        val localSummaries = mapOf(
            date to DateTodoSummary(
                date = date,
                todos = listOf(todoSummary(id = 1, title = "Local")),
                indicatorCount = 1,
                overflowCount = 0
            )
        )

        val merged = mergeTaskSurfaceSummaries(
            yearMonth = YearMonth.of(2026, 5),
            localSummaries = localSummaries,
            assignedTodos = listOf(
                assignedTodo(id = "assigned-1", title = "Assigned 1", dueDate = date),
                assignedTodo(id = "assigned-2", title = "Assigned 2", dueDate = date),
                assignedTodo(id = "assigned-3", title = "Assigned 3", dueDate = date),
                assignedTodo(id = "assigned-out", title = "Assigned out", dueDate = outOfMonthDate)
            ),
            maxIndicatorsPerDate = 3
        )

        val summary = merged.getValue(date)
        assertThat(summary.todos.map { it.title })
            .containsExactly("Local", "Assigned 1", "Assigned 2", "Assigned 3")
            .inOrder()
        assertThat(summary.indicatorCount).isEqualTo(3)
        assertThat(summary.overflowCount).isEqualTo(1)
        assertThat(summary.todos.drop(1).map { it.createdAt })
            .containsExactly(ASSIGNED_CREATED_AT, ASSIGNED_CREATED_AT, ASSIGNED_CREATED_AT)
            .inOrder()
        assertThat(merged).doesNotContainKey(outOfMonthDate)
    }

    @Test
    fun observeTaskSurfaceSummariesCanMergeProvidedAssignedFlow() = runTest {
        val repository = FakeTodoRepository().apply {
            addTodo("Local", LocalDate.of(2026, 5, 16), null)
        }
        val useCase = ObserveTaskSurfaceSummariesUseCase(
            observeMonthlyTodoSummariesUseCase = ObserveMonthlyTodoSummariesUseCase(
                observeMonthlyTodosUseCase = ObserveMonthlyTodosUseCase(repository)
            ),
            getAssignedTodosUseCase = GetAssignedTodosUseCase(FakeAssignmentRepository())
        )

        val summaries = useCase(
            yearMonth = YearMonth.of(2026, 5),
            assignedTodos = flowOf(
                listOf(
                    assignedTodo(
                        id = "assigned",
                        title = "Assigned",
                        dueDate = LocalDate.of(2026, 5, 16)
                    )
                )
            )
        ).first()

        assertThat(summaries.getValue(LocalDate.of(2026, 5, 16)).todos.map { it.title })
            .containsExactly("Local", "Assigned")
            .inOrder()
    }

    private fun todo(
        id: Long,
        title: String,
        dueDate: LocalDate?,
        isDone: Boolean = false,
        priority: TodoPriority
    ): TodoItem =
        TodoItem(
            id = id,
            title = title,
            isDone = isDone,
            dueDate = dueDate,
            createdAt = id,
            updatedAt = id,
            categoryId = null,
            priority = priority
        )

    private fun todoSummary(id: Long, title: String): TodoSummary =
        TodoSummary(
            id = id,
            title = title,
            isDone = false,
            priority = TodoPriority.MEDIUM
        )

    private fun assignedTodo(
        id: String,
        title: String = "Assigned todo",
        dueDate: LocalDate? = null,
        status: AssignedTodoStatus = AssignedTodoStatus.ACCEPTED,
        priority: TodoPriority = TodoPriority.MEDIUM,
        dueTimeMinutes: Int? = null,
        senderNickname: String = "monday",
        assignmentMode: AssignmentMode = AssignmentMode.REQUEST
    ): AssignedTodo =
        AssignedTodo(
            id = id,
            bundleId = "bundle-$id",
            title = title,
            description = null,
            dueDate = dueDate,
            dueTimeMinutes = dueTimeMinutes,
            priority = priority,
            category = null,
            status = status,
            terminalReason = null,
            progressPercent = if (status == AssignedTodoStatus.DONE) 100 else 0,
            sender = AssignedTodoUser(id = "friend-1", nickname = senderNickname),
            receiver = AssignedTodoUser(id = "me", nickname = "tester"),
            assignmentMode = assignmentMode,
            reminder = null,
            checklist = emptyList(),
            createdAt = Instant.ofEpochMilli(ASSIGNED_CREATED_AT)
        )

    private class FakeAssignmentRepository : AssignmentRepository {
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
            Result.success(emptyList())

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            error("unused")

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            flowOf(emptyList())

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

    private companion object {
        private const val ASSIGNED_CREATED_AT = 1_768_521_600_000L
    }
}
