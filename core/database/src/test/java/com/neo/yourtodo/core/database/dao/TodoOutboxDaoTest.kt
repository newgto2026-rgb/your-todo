package com.neo.yourtodo.core.database.dao

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TodoOutboxDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: TodoOutboxDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.todoOutboxDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getPendingMutations_returnsOwnerMutationsInCreatedOrder() = runTest {
        dao.insert(outbox(ownerUserId = "user-b", createdAt = 1L))
        dao.insert(outbox(ownerUserId = "user-a", createdAt = 3L, clientMutationId = "m3"))
        dao.insert(outbox(ownerUserId = "user-a", createdAt = 1L, clientMutationId = "m1"))
        dao.insert(outbox(ownerUserId = "user-a", createdAt = 2L, clientMutationId = "m2"))

        val result = dao.getPendingMutations("user-a")

        assertThat(result.map { it.clientMutationId }).containsExactly("m1", "m2", "m3").inOrder()
    }

    @Test
    fun deleteByOwner_removesOnlyThatOwnerOutbox() = runTest {
        dao.insert(outbox(ownerUserId = "user-a", clientMutationId = "a"))
        dao.insert(outbox(ownerUserId = "user-b", clientMutationId = "b"))

        dao.deleteByOwner("user-a")

        assertThat(dao.getPendingMutations("user-a")).isEmpty()
        assertThat(dao.getPendingMutations("user-b").map { it.clientMutationId }).containsExactly("b")
    }

    private fun outbox(
        ownerUserId: String,
        clientMutationId: String = "mutation",
        createdAt: Long = 1L
    ): TodoOutboxEntity =
        TodoOutboxEntity(
            ownerUserId = ownerUserId,
            clientMutationId = clientMutationId,
            todoLocalId = null,
            serverId = null,
            clientId = null,
            type = "CREATE",
            payloadJson = "{}",
            createdAt = createdAt
        )
}
