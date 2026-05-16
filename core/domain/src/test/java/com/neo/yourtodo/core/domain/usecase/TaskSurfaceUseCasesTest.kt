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
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoReminder
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
import java.time.ZoneId
import java.time.YearMonth
import kotlin.math.abs
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
            zoneId = TEST_ZONE_ID,
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
            today = today,
            zoneId = TEST_ZONE_ID
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
    fun buildTaskSurfaceListDueDateSectionsRespectAssignedOverrideMatrix() {
        val today = LocalDate.of(2026, 5, 16)

        val surface = BuildTaskSurfaceListUseCase()(
            localTodos = listOf(
                todo(id = 1, title = "Local later", dueDate = today.plusDays(1), priority = TodoPriority.LOW),
                todo(id = 2, title = "Local no date", dueDate = null, priority = TodoPriority.HIGH)
            ),
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-active",
                    title = "Assigned active override",
                    dueDate = today,
                    status = AssignedTodoStatus.DONE,
                    priority = TodoPriority.MEDIUM,
                    dueTimeMinutes = 10 * 60
                ),
                assignedTodo(
                    id = "assigned-completed",
                    title = "Assigned completed override",
                    dueDate = today.minusDays(1),
                    status = AssignedTodoStatus.ACCEPTED,
                    priority = TodoPriority.HIGH,
                    dueTimeMinutes = 9 * 60
                ),
                assignedTodo(
                    id = "assigned-hidden",
                    title = "Assigned hidden",
                    dueDate = today,
                    status = AssignedTodoStatus.ACCEPTED,
                    priority = TodoPriority.HIGH,
                    dueTimeMinutes = 8 * 60
                )
            ),
            selectedFilter = TodoFilter.ALL,
            selectedPriorityFilter = TodoPriorityFilter.ALL,
            selectedSortOption = TodoSortOption.DUE_DATE,
            today = today,
            zoneId = TEST_ZONE_ID,
            assignedOverrides = AssignedTaskSurfaceOverrides(
                completedIds = setOf("assigned-completed"),
                activeIds = setOf("assigned-active"),
                hiddenIds = setOf("assigned-hidden")
            )
        )

        assertThat(surface.sections.map { it.key }).containsExactly(
            TaskSurfaceSectionKey.DueDate(today),
            TaskSurfaceSectionKey.DueDate(today.plusDays(1)),
            TaskSurfaceSectionKey.DueDate(null),
            TaskSurfaceSectionKey.Completed
        ).inOrder()
        assertThat(surface.items.map { it.title }).containsExactly(
            "Assigned active override",
            "Local later",
            "Local no date",
            "Assigned completed override"
        ).inOrder()
        assertThat(surface.completedAssignedTodoIds).containsExactly("assigned-completed")
    }

    @Test
    fun buildTaskSurfaceListNonAllFiltersKeepPlannerPriorityOrderingAcrossSortOptions() {
        val today = LocalDate.of(2026, 5, 16)

        val surface = BuildTaskSurfaceListUseCase()(
            localTodos = listOf(
                todo(
                    id = 1,
                    title = "Low overdue",
                    dueDate = today.minusDays(1),
                    priority = TodoPriority.LOW
                ),
                todo(
                    id = 2,
                    title = "High today",
                    dueDate = today,
                    priority = TodoPriority.HIGH
                )
            ),
            assignedTodos = emptyList(),
            selectedFilter = TodoFilter.TODAY,
            selectedPriorityFilter = TodoPriorityFilter.ALL,
            selectedSortOption = TodoSortOption.DUE_DATE,
            today = today,
            zoneId = TEST_ZONE_ID
        )

        assertThat(surface.sections).isEmpty()
        assertThat(surface.items.map { it.title }).containsExactly(
            "High today",
            "Low overdue"
        ).inOrder()
    }

    @Test
    fun buildTaskSurfaceListTodayFilterCombinesDatePriorityAndAssignedOverrides() {
        val today = LocalDate.of(2026, 5, 16)

        val surface = BuildTaskSurfaceListUseCase()(
            localTodos = listOf(
                todo(id = 1, title = "High overdue", dueDate = today.minusDays(1), priority = TodoPriority.HIGH),
                todo(id = 2, title = "High future", dueDate = today.plusDays(1), priority = TodoPriority.HIGH),
                todo(id = 3, title = "Medium today", dueDate = today, priority = TodoPriority.MEDIUM)
            ),
            assignedTodos = listOf(
                assignedTodo(
                    id = "assigned-done-high",
                    title = "Assigned done forced active",
                    dueDate = today,
                    status = AssignedTodoStatus.DONE,
                    priority = TodoPriority.HIGH,
                    dueTimeMinutes = 8 * 60
                ),
                assignedTodo(
                    id = "assigned-open-high",
                    title = "Assigned open forced completed",
                    dueDate = today,
                    status = AssignedTodoStatus.ACCEPTED,
                    priority = TodoPriority.HIGH,
                    dueTimeMinutes = 7 * 60
                ),
                assignedTodo(
                    id = "assigned-hidden-high",
                    title = "Assigned hidden high",
                    dueDate = today,
                    status = AssignedTodoStatus.ACCEPTED,
                    priority = TodoPriority.HIGH
                )
            ),
            selectedFilter = TodoFilter.TODAY,
            selectedPriorityFilter = TodoPriorityFilter.HIGH,
            selectedSortOption = TodoSortOption.FRIEND,
            today = today,
            zoneId = TEST_ZONE_ID,
            assignedOverrides = AssignedTaskSurfaceOverrides(
                activeIds = setOf("assigned-done-high"),
                completedIds = setOf("assigned-open-high"),
                hiddenIds = setOf("assigned-hidden-high")
            )
        )

        assertThat(surface.sections).isEmpty()
        assertThat(surface.items.map { it.title }).containsExactly(
            "Assigned done forced active",
            "High overdue"
        ).inOrder()
        assertThat(surface.completedAssignedTodoIds).containsExactly("assigned-open-high")
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
            ),
            zoneId = TEST_ZONE_ID
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
    fun buildTaskSurfaceDateTodosSortsAssignedByCompletionPriorityTimeAndTitle() {
        val selectedDate = LocalDate.of(2026, 5, 16)

        val items = BuildTaskSurfaceDateTodosUseCase()(
            selectedDate = selectedDate,
            localTodos = emptyList(),
            assignedTodos = listOf(
                assignedTodo(
                    id = "done-high",
                    title = "Done high early",
                    dueDate = selectedDate,
                    status = AssignedTodoStatus.DONE,
                    priority = TodoPriority.HIGH,
                    dueTimeMinutes = 8 * 60
                ),
                assignedTodo(
                    id = "active-low",
                    title = "Active low early",
                    dueDate = selectedDate,
                    priority = TodoPriority.LOW,
                    dueTimeMinutes = 7 * 60
                ),
                assignedTodo(
                    id = "active-high-late",
                    title = "Active high late",
                    dueDate = selectedDate,
                    priority = TodoPriority.HIGH,
                    dueTimeMinutes = 10 * 60
                ),
                assignedTodo(
                    id = "active-high-early-b",
                    title = "Beta high early",
                    dueDate = selectedDate,
                    priority = TodoPriority.HIGH,
                    dueTimeMinutes = 9 * 60
                ),
                assignedTodo(
                    id = "active-high-early-a",
                    title = "Alpha high early",
                    dueDate = selectedDate,
                    priority = TodoPriority.HIGH,
                    dueTimeMinutes = 9 * 60
                )
            ),
            zoneId = TEST_ZONE_ID
        )

        assertThat(items.map { it.title }).containsExactly(
            "Alpha high early",
            "Beta high early",
            "Active high late",
            "Active low early",
            "Done high early"
        ).inOrder()
    }

    @Test
    fun assignedTaskSurfaceRowIdKeepsExistingNegativeHashPolicy() {
        val id = "assigned-id"

        val rowId = assignedTaskSurfaceRowId(id)

        assertThat(rowId).isEqualTo(-abs(id.hashCode().toLong()) - 1)
        assertThat(rowId).isLessThan(0L)
    }

    @Test
    fun assignedTodoReminderLeadMinutesUseExplicitZoneId() {
        val dueDate = LocalDate.of(2026, 5, 16)
        val reminderAt = dueDate
            .atTime(8, 30)
            .atZone(TEST_ZONE_ID)
            .toInstant()
            .toString()
        val assignedTodo = assignedTodo(
            id = "assigned-reminder",
            dueDate = dueDate,
            dueTimeMinutes = 9 * 60,
            reminder = AssignedTodoReminder(reminderAt = reminderAt, enabled = true)
        )

        val itemInTestZone = assignedTodo.toTaskSurfaceItem(zoneId = TEST_ZONE_ID)
        val itemInUtc = assignedTodo.toTaskSurfaceItem(zoneId = ZoneId.of("UTC"))

        assertThat(itemInTestZone.reminderLeadMinutes).isEqualTo(30)
        assertThat(itemInUtc.reminderLeadMinutes).isNull()
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
    fun mergeTaskSurfaceSummariesHandlesAssignedOnlyDatesAndZeroIndicatorLimit() {
        val date = LocalDate.of(2026, 5, 16)

        val merged = mergeTaskSurfaceSummaries(
            yearMonth = YearMonth.of(2026, 5),
            localSummaries = emptyMap(),
            assignedTodos = listOf(
                assignedTodo(id = "assigned-1", title = "Assigned 1", dueDate = date),
                assignedTodo(id = "assigned-2", title = "Assigned 2", dueDate = date)
            ),
            maxIndicatorsPerDate = 0
        )

        val summary = merged.getValue(date)
        assertThat(summary.todos.map { it.title }).containsExactly("Assigned 1", "Assigned 2").inOrder()
        assertThat(summary.indicatorCount).isEqualTo(0)
        assertThat(summary.overflowCount).isEqualTo(2)
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
        assignmentMode: AssignmentMode = AssignmentMode.REQUEST,
        reminder: AssignedTodoReminder? = null
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
            reminder = reminder,
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
        private val TEST_ZONE_ID = ZoneId.of("Asia/Seoul")
    }
}
