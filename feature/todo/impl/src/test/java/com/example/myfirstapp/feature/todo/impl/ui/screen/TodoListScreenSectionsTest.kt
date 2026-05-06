package com.example.myfirstapp.feature.todo.impl.ui

import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.feature.todo.impl.R
import com.example.myfirstapp.feature.todo.impl.model.TodoItemUiModel
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class TodoListScreenSectionsTest {

    @Test
    fun todayPlannerSectionsIncludesHighPriorityItemsOutsideTodayBuckets() {
        val today = LocalDate.now()
        val items = listOf(
            item(id = 1L, dueDate = today.minusDays(1), priority = TodoPriority.MEDIUM),
            item(id = 2L, dueDate = today, dueTimeText = "09:00", priority = TodoPriority.MEDIUM),
            item(id = 3L, dueDate = today, dueTimeText = null, priority = TodoPriority.MEDIUM),
            item(id = 4L, dueDate = null, priority = TodoPriority.HIGH),
            item(id = 5L, dueDate = today.plusDays(1), priority = TodoPriority.HIGH)
        )

        val sections = todayPlannerSections(items)
        val highPrioritySection = sections.first { it.titleRes == R.string.todo_today_section_high_priority }

        assertThat(highPrioritySection.items.map { it.id }).containsExactly(4L, 5L)
    }

    private fun item(
        id: Long,
        dueDate: LocalDate?,
        dueTimeText: String? = null,
        priority: TodoPriority
    ): TodoItemUiModel = TodoItemUiModel(
        id = id,
        title = "task-$id",
        isDone = false,
        dueDate = dueDate,
        dueDateText = dueDate?.toString(),
        dueTimeText = dueTimeText,
        reminderAtEpochMillis = null,
        reminderDateTimeText = null,
        isReminderEnabled = false,
        reminderLeadMinutes = null,
        reminderRepeatType = ReminderRepeatType.NONE,
        priority = priority
    )
}
