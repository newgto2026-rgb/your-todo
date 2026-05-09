package com.neo.yourtodo.feature.todo.impl.ui

import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class TodoListScreenSectionsTest {

    @Test
    fun todayPlannerSectionsUseOnlyDueDateBuckets() {
        val today = LocalDate.now()
        val items = listOf(
            item(id = 1L, dueDate = today.minusDays(1), priority = TodoPriority.MEDIUM),
            item(id = 2L, dueDate = today, dueTimeText = "09:00", priority = TodoPriority.MEDIUM),
            item(id = 3L, dueDate = today, dueTimeText = null, priority = TodoPriority.MEDIUM),
            item(id = 4L, dueDate = null, priority = TodoPriority.HIGH),
            item(id = 5L, dueDate = today.plusDays(1), priority = TodoPriority.HIGH)
        )

        val sections = todayPlannerSections(items)

        assertThat(sections.map { it.titleRes }).containsExactly(
            R.string.todo_today_section_timed,
            R.string.todo_today_section_today,
            R.string.todo_today_section_overdue
        ).inOrder()
        assertThat(sections.flatMap { it.items }.map { it.id }).containsExactly(2L, 3L, 1L)
    }

    @Test
    fun formatDueDateLabelUsesProvidedDatePattern() {
        val label = formatDueDateLabel("2026-05-02", "M월 d일")

        assertThat(label).isEqualTo("5월 2일")
    }

    @Test
    fun formatDueDateLabelKeepsInvalidRawValue() {
        val label = formatDueDateLabel("May 2", "M월 d일")

        assertThat(label).isEqualTo("May 2")
    }

    @Test
    fun formatDateLabelFallsBackToIsoDateForInvalidPattern() {
        val label = formatDateLabel(LocalDate.of(2026, 5, 2), "MMMM '")

        assertThat(label).isEqualTo("2026-05-02")
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
