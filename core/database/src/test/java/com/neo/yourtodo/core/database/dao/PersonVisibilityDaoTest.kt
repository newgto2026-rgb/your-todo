package com.neo.yourtodo.core.database.dao

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.database.entity.ObservedSyncStateEntity
import com.neo.yourtodo.core.database.entity.ObservedTodoEntity
import com.neo.yourtodo.core.database.entity.VisibilityGrantEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PersonVisibilityDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PersonVisibilityDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.personVisibilityDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun visibilityGrantsAreScopedByCurrentUserAndPair() = runTest {
        dao.upsertVisibilityGrants(
            listOf(
                visibilityGrant(currentUserId = "viewer-a", grantId = "grant-1"),
                visibilityGrant(currentUserId = "viewer-b", grantId = "grant-1")
            )
        )

        val viewerA = dao.observeVisibilityGrants("viewer-a").first()
        val active = dao.getActiveGrant(
            currentUserId = "viewer-a",
            ownerUserId = "owner",
            viewerUserId = "viewer"
        )

        assertThat(viewerA.map { it.currentUserId to it.grantId }).containsExactly("viewer-a" to "grant-1")
        assertThat(active?.grantId).isEqualTo("grant-1")
        assertThat(dao.observeVisibilityGrants("viewer-b").first().map { it.currentUserId })
            .containsExactly("viewer-b")
    }

    @Test
    fun applyObservedTodoSyncUpsertsDeletesAndPurgesByGrant() = runTest {
        dao.upsertObservedTodos(
            listOf(
                observedTodo(observedTodoId = "stale-deleted", grantId = "grant-1"),
                observedTodo(observedTodoId = "stale-purged", grantId = "grant-2"),
                observedTodo(currentUserId = "viewer-b", observedTodoId = "other-user", grantId = "grant-2")
            )
        )

        dao.applyObservedTodoSync(
            currentUserId = "viewer-a",
            upserts = listOf(observedTodo(observedTodoId = "fresh", grantId = "grant-1", title = "fresh")),
            deletedObservedTodoIds = listOf("stale-deleted"),
            purgedGrantIds = listOf("grant-2"),
            state = ObservedSyncStateEntity(
                currentUserId = "viewer-a",
                cursor = "cursor-2",
                syncedAtEpochMillis = 300L
            )
        )

        assertThat(dao.observeObservedTodos("viewer-a").first().map { it.observedTodoId })
            .containsExactly("fresh")
        assertThat(dao.observeObservedTodos("viewer-b").first().map { it.observedTodoId })
            .containsExactly("other-user")
        assertThat(dao.getObservedSyncState("viewer-a")?.cursor).isEqualTo("cursor-2")
    }

    @Test
    fun applyObservedTodoSyncPurgesGrantBeforeUpsertingFreshRowsForSameGrant() = runTest {
        dao.upsertObservedTodos(
            listOf(
                observedTodo(observedTodoId = "stale", grantId = "grant-1")
            )
        )

        dao.applyObservedTodoSync(
            currentUserId = "viewer-a",
            upserts = listOf(observedTodo(observedTodoId = "fresh", grantId = "grant-1")),
            deletedObservedTodoIds = emptyList(),
            purgedGrantIds = listOf("grant-1"),
            state = ObservedSyncStateEntity(
                currentUserId = "viewer-a",
                cursor = "cursor-2",
                syncedAtEpochMillis = 300L
            )
        )

        assertThat(dao.observeObservedTodos("viewer-a").first().map { it.observedTodoId })
            .containsExactly("fresh")
    }

    @Test
    fun purgeObservedTodosByGrantIdOnlyDeletesMatchingGrantForCurrentUser() = runTest {
        dao.upsertObservedTodos(
            listOf(
                observedTodo(observedTodoId = "grant-1-a", grantId = "grant-1"),
                observedTodo(observedTodoId = "grant-2-a", grantId = "grant-2"),
                observedTodo(currentUserId = "viewer-b", observedTodoId = "grant-1-b", grantId = "grant-1")
            )
        )

        dao.purgeObservedTodosByGrantId(currentUserId = "viewer-a", grantId = "grant-1")

        assertThat(dao.observeObservedTodos("viewer-a").first().map { it.observedTodoId })
            .containsExactly("grant-2-a")
        assertThat(dao.observeObservedTodos("viewer-b").first().map { it.observedTodoId })
            .containsExactly("grant-1-b")
    }

    @Test
    fun replaceVisibilityGrantsAndPruneObservedTodosDeletesRowsForInactiveObservedGrants() = runTest {
        dao.upsertObservedTodos(
            listOf(
                observedTodo(observedTodoId = "kept", grantId = "grant-1"),
                observedTodo(observedTodoId = "removed", grantId = "grant-2"),
                observedTodo(currentUserId = "viewer-b", observedTodoId = "other-user", grantId = "grant-2")
            )
        )

        dao.replaceVisibilityGrantsAndPruneObservedTodos(
            currentUserId = "viewer-a",
            grants = listOf(visibilityGrant(grantId = "grant-1")),
            activeObservedGrantIds = listOf("grant-1")
        )

        assertThat(dao.observeObservedTodos("viewer-a").first().map { it.observedTodoId })
            .containsExactly("kept")
        assertThat(dao.observeObservedTodos("viewer-b").first().map { it.observedTodoId })
            .containsExactly("other-user")
    }

    @Test
    fun replaceVisibilityGrantsAndPruneObservedTodosClearsRowsWhenNoObservedGrantsRemain() = runTest {
        dao.upsertObservedTodos(
            listOf(
                observedTodo(observedTodoId = "removed", grantId = "grant-1"),
                observedTodo(currentUserId = "viewer-b", observedTodoId = "other-user", grantId = "grant-1")
            )
        )

        dao.replaceVisibilityGrantsAndPruneObservedTodos(
            currentUserId = "viewer-a",
            grants = emptyList(),
            activeObservedGrantIds = emptyList()
        )

        assertThat(dao.observeObservedTodos("viewer-a").first()).isEmpty()
        assertThat(dao.observeObservedTodos("viewer-b").first().map { it.observedTodoId })
            .containsExactly("other-user")
    }

    private fun visibilityGrant(
        currentUserId: String = "viewer-a",
        grantId: String = "grant-1",
        status: String = "ACTIVE"
    ) = VisibilityGrantEntity(
        currentUserId = currentUserId,
        grantId = grantId,
        ownerUserId = "owner",
        viewerUserId = "viewer",
        status = status,
        version = 1,
        createdAtEpochMillis = 100L,
        updatedAtEpochMillis = 200L,
        revokedAtEpochMillis = null
    )

    private fun observedTodo(
        currentUserId: String = "viewer-a",
        observedTodoId: String = "observed-1",
        grantId: String = "grant-1",
        title: String = observedTodoId
    ) = ObservedTodoEntity(
        currentUserId = currentUserId,
        observedTodoId = observedTodoId,
        sourceTodoId = "source-$observedTodoId",
        grantId = grantId,
        ownerUserId = "owner",
        ownerNickname = "owner",
        ownerAvatarUrl = null,
        title = title,
        dueDateEpochDay = null,
        dueTimeMinutes = null,
        isDone = false,
        recurrenceOccurrenceId = null,
        projectionVersion = 1,
        updatedAtEpochMillis = 100L,
        cacheUpdatedAtEpochMillis = 200L
    )
}
