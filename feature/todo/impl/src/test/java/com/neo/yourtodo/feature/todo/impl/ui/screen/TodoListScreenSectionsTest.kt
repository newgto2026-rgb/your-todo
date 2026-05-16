package com.neo.yourtodo.feature.todo.impl.ui.screen

import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSortOption
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel
import com.neo.yourtodo.feature.todo.impl.ui.TodoListUiState
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

        val sections = todayPlannerSections(items, today)

        assertThat(sections.map { it.titleRes }).containsExactly(
            R.string.todo_today_section_timed,
            R.string.todo_today_section_today,
            R.string.todo_today_section_overdue
        ).inOrder()
        assertThat(sections.flatMap { it.items }.map { it.id }).containsExactly(2L, 3L, 1L)
    }

    @Test
    fun todayPlannerSectionsIgnoreCompletedAndNonTodayItems() {
        val today = LocalDate.now()
        val items = listOf(
            item(id = 1L, dueDate = today.minusDays(1), isDone = true, priority = TodoPriority.HIGH),
            item(id = 2L, dueDate = today, dueTimeText = "09:00", isDone = true, priority = TodoPriority.HIGH),
            item(id = 3L, dueDate = today.plusDays(1), priority = TodoPriority.HIGH),
            item(id = 4L, dueDate = null, priority = TodoPriority.HIGH),
            item(id = 5L, dueDate = today, dueTimeText = "10:00", priority = TodoPriority.MEDIUM),
            item(id = 6L, dueDate = today, priority = TodoPriority.LOW),
            item(id = 7L, dueDate = today.minusDays(1), priority = TodoPriority.MEDIUM)
        )

        val sections = todayPlannerSections(items, today)

        assertThat(sections[0].items.map { it.id }).containsExactly(5L)
        assertThat(sections[1].items.map { it.id }).containsExactly(6L)
        assertThat(sections[2].items.map { it.id }).containsExactly(7L)
        assertThat(sections.flatMap { it.items }.map { it.id })
            .containsNoneOf(1L, 2L, 3L, 4L)
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

    @Test
    fun headerTextForSelectsCopyForCurrentFilter() {
        assertThat(
            headerTextFor(
                filter = TodoFilter.ALL,
                allTitle = "All",
                allSubtitle = "All subtitle",
                todayTitle = "Today",
                todaySubtitle = "Today subtitle",
                completedTitle = "Done",
                completedSubtitle = "Done subtitle"
            )
        ).isEqualTo("All" to "All subtitle")

        assertThat(
            headerTextFor(
                filter = TodoFilter.TODAY,
                allTitle = "All",
                allSubtitle = "All subtitle",
                todayTitle = "Today",
                todaySubtitle = "Today subtitle",
                completedTitle = "Done",
                completedSubtitle = "Done subtitle"
            )
        ).isEqualTo("Today" to "Today subtitle")

        assertThat(
            headerTextFor(
                filter = TodoFilter.COMPLETED,
                allTitle = "All",
                allSubtitle = "All subtitle",
                todayTitle = "Today",
                todaySubtitle = "Today subtitle",
                completedTitle = "Done",
                completedSubtitle = "Done subtitle"
            )
        ).isEqualTo("Done" to "Done subtitle")
    }

    @Test
    fun sortOptionSubtitleResourcesMatchAllHeaderModes() {
        assertThat(TodoSortOption.DEFAULT.subtitleRes()).isEqualTo(R.string.todo_header_all_subtitle)
        assertThat(TodoSortOption.DUE_DATE.subtitleRes())
            .isEqualTo(R.string.todo_header_all_subtitle_due_date)
        assertThat(TodoSortOption.PRIORITY.subtitleRes())
            .isEqualTo(R.string.todo_header_all_subtitle_priority)
        assertThat(TodoSortOption.FRIEND.subtitleRes())
            .isEqualTo(R.string.todo_header_all_subtitle_friend)
    }

    @Test
    fun completionProgressCountsDoneItemsInRenderedList() {
        val uiState = TodoListUiState(
            items = listOf(
                item(id = 1L, dueDate = null, isDone = true, priority = TodoPriority.LOW),
                item(id = 2L, dueDate = null, priority = TodoPriority.MEDIUM),
                item(id = 3L, dueDate = null, isDone = true, priority = TodoPriority.HIGH),
                item(id = 4L, dueDate = null, priority = TodoPriority.HIGH)
            )
        )

        assertThat(completionProgress(uiState)).isEqualTo(0.5f)
        assertThat(completionProgress(TodoListUiState())).isEqualTo(0f)
    }

    private fun item(
        id: Long,
        dueDate: LocalDate?,
        dueTimeText: String? = null,
        isDone: Boolean = false,
        priority: TodoPriority
    ): TodoItemUiModel = TodoItemUiModel(
        id = id,
        title = "task-$id",
        isDone = isDone,
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
