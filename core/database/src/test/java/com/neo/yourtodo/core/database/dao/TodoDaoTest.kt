package com.neo.yourtodo.core.database.dao

import androidx.room.Room
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class TodoDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: TodoDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.todoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetTodoById_returnsInsertedTodo() = runTest {
        val id = dao.insert(
            TodoEntity(
                title = "todo",
                isDone = false,
                dueDateEpochDay = 20000L,
                createdAt = 100L,
                updatedAt = 100L,
                categoryId = null
            )
        )

        val saved = dao.getTodoById(id)

        assertThat(saved).isNotNull()
        assertThat(saved?.id).isEqualTo(id)
        assertThat(saved?.title).isEqualTo("todo")
        assertThat(saved?.dueDateEpochDay).isEqualTo(20000L)
    }

    @Test
    fun observeTodos_ordersByCreatedAtDesc() = runTest {
        dao.insert(
            TodoEntity(
                title = "older",
                isDone = false,
                dueDateEpochDay = null,
                createdAt = 100L,
                updatedAt = 100L,
                categoryId = null
            )
        )
        dao.insert(
            TodoEntity(
                title = "newer",
                isDone = false,
                dueDateEpochDay = null,
                createdAt = 300L,
                updatedAt = 300L,
                categoryId = null
            )
        )
        dao.insert(
            TodoEntity(
                title = "middle",
                isDone = false,
                dueDateEpochDay = null,
                createdAt = 200L,
                updatedAt = 200L,
                categoryId = null
            )
        )

        val list = dao.observeTodos().first()

        assertThat(list.map { it.title }).containsExactly("newer", "middle", "older").inOrder()
    }

    @Test
    fun observeTodosByDueDateRange_includesMonthBoundariesOnly() = runTest {
        dao.insert(
            TodoEntity(
                title = "before",
                isDone = false,
                dueDateEpochDay = 20543L,
                createdAt = 10L,
                updatedAt = 10L,
                categoryId = null
            )
        )
        dao.insert(
            TodoEntity(
                title = "start",
                isDone = false,
                dueDateEpochDay = 20544L,
                createdAt = 20L,
                updatedAt = 20L,
                categoryId = null
            )
        )
        dao.insert(
            TodoEntity(
                title = "end",
                isDone = false,
                dueDateEpochDay = 20573L,
                createdAt = 30L,
                updatedAt = 30L,
                categoryId = null
            )
        )
        dao.insert(
            TodoEntity(
                title = "after",
                isDone = false,
                dueDateEpochDay = 20574L,
                createdAt = 40L,
                updatedAt = 40L,
                categoryId = null
            )
        )
        dao.insert(
            TodoEntity(
                title = "no-date",
                isDone = false,
                dueDateEpochDay = null,
                createdAt = 50L,
                updatedAt = 50L,
                categoryId = null
            )
        )

        val list = dao.observeTodosByDueDateRange(20544L, 20573L).first()

        assertThat(list.map { it.title }).containsExactly("start", "end").inOrder()
    }

    @Test
    fun update_persistsChanges() = runTest {
        val id = dao.insert(
            TodoEntity(
                title = "before",
                isDone = false,
                dueDateEpochDay = null,
                createdAt = 1L,
                updatedAt = 1L,
                categoryId = null
            )
        )
        val existing = dao.getTodoById(id)!!

        dao.update(
            existing.copy(
                title = "after",
                isDone = true,
                dueDateEpochDay = 20500L,
                updatedAt = 2L
            )
        )

        val updated = dao.getTodoById(id)

        assertThat(updated?.title).isEqualTo("after")
        assertThat(updated?.isDone).isTrue()
        assertThat(updated?.dueDateEpochDay).isEqualTo(20500L)
        assertThat(updated?.updatedAt).isEqualTo(2L)
    }

    @Test
    fun delete_removesRow() = runTest {
        val id = dao.insert(
            TodoEntity(
                title = "to-delete",
                isDone = false,
                dueDateEpochDay = null,
                createdAt = 1L,
                updatedAt = 1L,
                categoryId = null
            )
        )
        val existing = dao.getTodoById(id)!!

        dao.delete(existing)

        assertThat(dao.getTodoById(id)).isNull()
    }
}
