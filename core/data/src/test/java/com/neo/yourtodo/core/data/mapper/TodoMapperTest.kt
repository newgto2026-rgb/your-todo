package com.neo.yourtodo.core.data.mapper

import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.model.TodoItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class TodoMapperTest {

    @Test
    fun toDomain_mapsDueDateEpochDay() {
        val entity = TodoEntity(
            id = 1L,
            title = "todo",
            isDone = true,
            dueDateEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
            createdAt = 100L,
            updatedAt = 200L,
            categoryId = 11L
        )

        val domain = entity.toDomain()

        assertThat(domain).isEqualTo(
            TodoItem(
                id = 1L,
                title = "todo",
                isDone = true,
                dueDate = LocalDate.of(2026, 4, 10),
                createdAt = 100L,
                updatedAt = 200L,
                categoryId = 11L
            )
        )
    }

    @Test
    fun toEntity_mapsNullDueDate() {
        val item = TodoItem(
            id = 3L,
            title = "title",
            isDone = false,
            dueDate = null,
            createdAt = 111L,
            updatedAt = 222L,
            categoryId = null
        )

        val entity = item.toEntity()

        assertThat(entity).isEqualTo(
            TodoEntity(
                id = 3L,
                title = "title",
                isDone = false,
                dueDateEpochDay = null,
                createdAt = 111L,
                updatedAt = 222L,
                categoryId = null
            )
        )
    }
}
