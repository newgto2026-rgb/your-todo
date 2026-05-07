package com.neo.yourtodo.feature.todo.impl.ui

import androidx.annotation.StringRes
import com.neo.yourtodo.feature.todo.impl.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class TodoDraftValidationResult(
    val normalizedTitle: String? = null,
    val parsedDueDate: LocalDate? = null,
    val parsedDueTimeMinutes: Int? = null,
    val reminderAtEpochMillis: Long? = null,
    @StringRes val errorMessageRes: Int? = null
)

internal fun validateTodoDraft(state: TodoListUiState): TodoDraftValidationResult {
    val normalizedTitle = state.draftTitle.trim()
    if (normalizedTitle.isBlank()) {
        return TodoDraftValidationResult(errorMessageRes = R.string.todo_error_title_required)
    }

    val parsedDueDate = parseIsoDateInput(state.draftDueDateInput)
    if (state.draftDueDateInput.isNotBlank() && parsedDueDate == null) {
        return TodoDraftValidationResult(errorMessageRes = R.string.todo_error_due_date_format)
    }

    val parsedDueTimeMinutes = dueTimeTextToMinutes(state.draftDueTimeInput)
    if (state.draftDueTimeInput.isNotBlank() && parsedDueTimeMinutes == null) {
        return TodoDraftValidationResult(errorMessageRes = R.string.todo_error_due_time_format)
    }
    if (state.draftDueTimeInput.isNotBlank() && parsedDueDate == null) {
        return TodoDraftValidationResult(errorMessageRes = R.string.todo_error_due_time_requires_due_date)
    }

    if (state.draftReminderEnabled) {
        if (parsedDueDate == null) {
            return TodoDraftValidationResult(errorMessageRes = R.string.todo_error_reminder_due_date_required)
        }
        if (parsedDueTimeMinutes == null) {
            return TodoDraftValidationResult(errorMessageRes = R.string.todo_error_reminder_due_time_required)
        }
    }

    val reminderAtEpochMillis = if (state.draftReminderEnabled) {
        val leadMinutes = state.draftReminderLeadMinutes ?: DEFAULT_REMINDER_LEAD_MINUTES
        dueDateTimeToEpochMillis(
            dueDate = checkNotNull(parsedDueDate),
            dueTimeMinutes = checkNotNull(parsedDueTimeMinutes)
        ) - leadMinutes * 60_000L
    } else {
        null
    }
    if (state.draftReminderEnabled && reminderAtEpochMillis != null && reminderAtEpochMillis <= System.currentTimeMillis()) {
        return TodoDraftValidationResult(errorMessageRes = R.string.todo_error_reminder_time_in_past)
    }

    return TodoDraftValidationResult(
        normalizedTitle = normalizedTitle,
        parsedDueDate = parsedDueDate,
        parsedDueTimeMinutes = parsedDueTimeMinutes,
        reminderAtEpochMillis = reminderAtEpochMillis
    )
}

internal fun parseIsoDateInput(value: String): LocalDate? {
    if (value.isBlank()) return null
    return runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
}
