package com.neo.yourtodo.core.database.dao

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.database.entity.AssignedTodoChecklistItemEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoEntity
import com.neo.yourtodo.core.database.entity.assignedTodoCacheKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AssignedTodoDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: AssignedTodoDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.assignedTodoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertAssignedTodoGraph_scopesRowsAndChecklistByOwner() = runTest {
        dao.upsertAssignedTodoGraph(
            items = listOf(
                assignedTodo(ownerUserId = "owner-a", id = "shared-id", title = "A item"),
                assignedTodo(ownerUserId = "owner-b", id = "shared-id", title = "B item")
            ),
            checklistItems = listOf(
                checklistItem(ownerUserId = "owner-a", assignedTodoId = "shared-id", id = "check-a"),
                checklistItem(ownerUserId = "owner-b", assignedTodoId = "shared-id", id = "check-b")
            )
        )

        val ownerA = dao.observeReceivedAssignedTodos("owner-a", listOf("ACCEPTED")).first()
        val ownerB = dao.observeReceivedAssignedTodos("owner-b", listOf("ACCEPTED")).first()

        assertThat(ownerA.map { it.assignedTodo.title }).containsExactly("A item")
        assertThat(ownerA.single().checklist.map { it.id }).containsExactly("check-a")
        assertThat(ownerB.map { it.assignedTodo.title }).containsExactly("B item")
        assertThat(ownerB.single().checklist.map { it.id }).containsExactly("check-b")
        assertThat(dao.getAssignedTodoById("owner-a", "shared-id")?.title).isEqualTo("A item")
        assertThat(dao.getAssignedTodoById("owner-b", "shared-id")?.title).isEqualTo("B item")
    }

    @Test
    fun upsertAssignedTodoGraph_replacesChecklistForSameOwnerItem() = runTest {
        dao.upsertAssignedTodoGraph(
            items = listOf(assignedTodo(ownerUserId = "owner-a", id = "assigned-1")),
            checklistItems = listOf(
                checklistItem(ownerUserId = "owner-a", assignedTodoId = "assigned-1", id = "old")
            )
        )

        dao.upsertAssignedTodoGraph(
            items = listOf(assignedTodo(ownerUserId = "owner-a", id = "assigned-1")),
            checklistItems = listOf(
                checklistItem(ownerUserId = "owner-a", assignedTodoId = "assigned-1", id = "new")
            )
        )

        val item = dao.observeReceivedAssignedTodos("owner-a", listOf("ACCEPTED")).first().single()

        assertThat(item.checklist.map { it.id }).containsExactly("new")
    }

    @Test
    fun hideReceivedFromTaskSurfaceExcludesOnlyTaskSurfaceObserver() = runTest {
        dao.upsertAssignedTodoGraph(
            items = listOf(
                assignedTodo(ownerUserId = "owner-a", id = "assigned-1", status = "DONE")
            ),
            checklistItems = emptyList()
        )

        dao.hideReceivedFromTaskSurface(ownerUserId = "owner-a", id = "assigned-1")

        assertThat(dao.observeReceivedAssignedTodos("owner-a", listOf("DONE")).first()).isEmpty()
        assertThat(
            dao.observeReceivedAssignedTodosByFriend(
                ownerUserId = "owner-a",
                friendUserId = "sender",
                statuses = listOf("DONE")
            ).first().map { it.assignedTodo.id }
        ).containsExactly("assigned-1")
    }

    @Test
    fun replaceReceivedCache_removesStaleRowsOnlyWithinOwnerAndStatusScope() = runTest {
        dao.upsertAssignedTodoGraph(
            items = listOf(
                assignedTodo(ownerUserId = "owner-a", id = "stale-active", title = "stale"),
                assignedTodo(ownerUserId = "owner-a", id = "pending", title = "pending", status = "PENDING"),
                assignedTodo(ownerUserId = "owner-b", id = "stale-active", title = "other owner")
            ),
            checklistItems = listOf(
                checklistItem(ownerUserId = "owner-a", assignedTodoId = "stale-active", id = "stale-check")
            )
        )

        dao.replaceReceivedCache(
            ownerUserId = "owner-a",
            statuses = listOf("ACCEPTED"),
            retainedIds = listOf("fresh-active"),
            items = listOf(assignedTodo(ownerUserId = "owner-a", id = "fresh-active", title = "fresh")),
            checklistItems = listOf(
                checklistItem(ownerUserId = "owner-a", assignedTodoId = "fresh-active", id = "fresh-check")
            )
        )

        val ownerAActive = dao.observeReceivedAssignedTodos("owner-a", listOf("ACCEPTED")).first()
        val ownerAPending = dao.observeReceivedAssignedTodos("owner-a", listOf("PENDING")).first()
        val ownerBActive = dao.observeReceivedAssignedTodos("owner-b", listOf("ACCEPTED")).first()

        assertThat(ownerAActive.map { it.assignedTodo.id }).containsExactly("fresh-active")
        assertThat(ownerAActive.single().checklist.map { it.id }).containsExactly("fresh-check")
        assertThat(ownerAPending.map { it.assignedTodo.id }).containsExactly("pending")
        assertThat(ownerBActive.map { it.assignedTodo.title }).containsExactly("other owner")
    }

    @Test
    fun replaceReceivedCache_hidesStaleTaskSurfaceRowsButKeepsFriendHistoryRows() = runTest {
        dao.upsertAssignedTodoGraph(
            items = listOf(
                assignedTodo(ownerUserId = "owner-a", id = "visible-done", status = "DONE"),
                assignedTodo(ownerUserId = "owner-a", id = "friend-history-done", status = "DONE")
            ),
            checklistItems = emptyList()
        )

        dao.replaceReceivedCache(
            ownerUserId = "owner-a",
            statuses = listOf("DONE"),
            retainedIds = listOf("visible-done"),
            items = listOf(assignedTodo(ownerUserId = "owner-a", id = "visible-done", status = "DONE")),
            checklistItems = emptyList()
        )

        assertThat(dao.observeReceivedAssignedTodos("owner-a", listOf("DONE")).first().map { it.assignedTodo.id })
            .containsExactly("visible-done")
        assertThat(
            dao.observeReceivedAssignedTodosByFriend(
                ownerUserId = "owner-a",
                friendUserId = "sender",
                statuses = listOf("DONE")
            ).first().map { it.assignedTodo.id }
        ).containsExactly("visible-done", "friend-history-done")
    }

    @Test
    fun deleteByOwner_cascadesChecklistOnlyForThatOwner() = runTest {
        dao.upsertAssignedTodoGraph(
            items = listOf(
                assignedTodo(ownerUserId = "owner-a", id = "same"),
                assignedTodo(ownerUserId = "owner-b", id = "same")
            ),
            checklistItems = listOf(
                checklistItem(ownerUserId = "owner-a", assignedTodoId = "same", id = "check-a"),
                checklistItem(ownerUserId = "owner-b", assignedTodoId = "same", id = "check-b")
            )
        )

        dao.deleteByOwner("owner-a")

        assertThat(dao.observeReceivedAssignedTodos("owner-a", listOf("ACCEPTED")).first()).isEmpty()
        val ownerB = dao.observeReceivedAssignedTodos("owner-b", listOf("ACCEPTED")).first().single()
        assertThat(ownerB.checklist.map { it.id }).containsExactly("check-b")
    }

    private fun assignedTodo(
        ownerUserId: String,
        id: String,
        title: String = id,
        status: String = "ACCEPTED",
        receivedCached: Boolean = true,
        sentCached: Boolean = false,
        senderUserId: String? = "sender",
        receiverUserId: String? = ownerUserId,
        createdAtEpochMillis: Long = 100L
    ) = AssignedTodoEntity(
        ownerUserId = ownerUserId,
        id = id,
        cacheKey = assignedTodoCacheKey(ownerUserId, id),
        bundleId = null,
        title = title,
        description = null,
        dueDateEpochDay = null,
        dueTimeMinutes = null,
        priority = "MEDIUM",
        category = null,
        status = status,
        terminalReason = null,
        progressPercent = 0,
        senderUserId = senderUserId,
        senderNickname = null,
        receiverUserId = receiverUserId,
        receiverNickname = null,
        assignmentMode = "REQUEST",
        reminderAt = null,
        reminderEnabled = null,
        createdAtEpochMillis = createdAtEpochMillis,
        completedAtEpochMillis = null,
        receivedCached = receivedCached,
        receivedTaskHidden = false,
        sentCached = sentCached,
        cacheUpdatedAt = 200L
    )

    private fun checklistItem(
        ownerUserId: String,
        assignedTodoId: String,
        id: String
    ) = AssignedTodoChecklistItemEntity(
        ownerUserId = ownerUserId,
        assignedTodoId = assignedTodoId,
        assignedTodoCacheKey = assignedTodoCacheKey(ownerUserId, assignedTodoId),
        id = id,
        title = id,
        completed = false,
        sortOrder = 0
    )
}
