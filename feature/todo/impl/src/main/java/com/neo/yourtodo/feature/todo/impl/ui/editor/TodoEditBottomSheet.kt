package com.neo.yourtodo.feature.todo.impl.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.feature.todo.impl.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTodoBottomSheet(
    sheetTitle: String,
    title: String,
    dueDateInput: String,
    dueTimeInput: String,
    reminderEnabled: Boolean,
    reminderLeadMinutes: Int,
    selectedPriority: TodoPriority,
    errorMessageRes: Int?,
    onTitleChange: (String) -> Unit,
    onDateInputChange: (String) -> Unit,
    onDueTimeInputChange: (String) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderLeadMinutesChange: (Int) -> Unit,
    onPrioritySelected: (TodoPriority) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean,
    contentEditable: Boolean = true
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
    }

    Surface(
        color = Color(0xFFF6F7FB),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier.testTag("todo_editor_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF323640)
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEAECF3),
                    onClick = onDismiss,
                    modifier = Modifier.testTag("task_edit_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = Color(0xFF5C6170)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.todo_editor_task_description),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF7A7F8C)
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = title,
                onValueChange = onTitleChange,
                enabled = contentEditable,
                placeholder = { Text(stringResource(R.string.todo_editor_task_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .testTag("task_title_input"),
                singleLine = false,
                shape = RoundedCornerShape(14.dp),
                isError = errorMessageRes != null,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFEBEDF4),
                    unfocusedContainerColor = Color(0xFFEBEDF4),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(12.dp))
            TodoEditorDueDateSelector(
                dueDateInput = dueDateInput,
                onDateInputChange = onDateInputChange,
                enabled = contentEditable,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("due_date_selector")
            )

            Spacer(Modifier.height(10.dp))
            TodoEditorDueTimeSelector(
                dueTimeInput = dueTimeInput,
                onDueTimeInputChange = onDueTimeInputChange,
                enabled = contentEditable,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("due_time_selector")
            )

            Spacer(Modifier.height(14.dp))
            TodoEditorPrioritySection(
                selectedPriority = selectedPriority,
                onPrioritySelected = onPrioritySelected,
                enabled = contentEditable
            )

            Spacer(Modifier.height(14.dp))
            TodoEditorReminderSection(
                reminderEnabled = reminderEnabled,
                reminderLeadMinutes = reminderLeadMinutes,
                onReminderEnabledChange = onReminderEnabledChange,
                onReminderLeadMinutesChange = onReminderLeadMinutesChange
            )

            if (errorMessageRes != null) {
                Text(
                    text = stringResource(errorMessageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showDelete) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.todo_editor_delete)) }
                }
                Button(
                    onClick = onSave,
                    enabled = title.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4A6697),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFB9C3D8),
                        disabledContentColor = Color(0xFFE9EDF7)
                    )
                ) {
                    Text(stringResource(R.string.todo_editor_save))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoEditorDueDateSelector(
    dueDateInput: String,
    onDateInputChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = isoDateToUtcMillis(dueDateInput)
    )

    Surface(
        onClick = { showDatePicker = true },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) Color(0xFFEBEDF4) else Color(0xFFE2E5EC)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFF5C6170)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = if (dueDateInput.isBlank()) {
                    stringResource(R.string.todo_editor_select_due_date)
                } else {
                    dueDateInput
                },
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !enabled -> Color(0xFF7A808C)
                    dueDateInput.isBlank() -> Color(0xFF8E94A3)
                    else -> Color(0xFF2F3441)
                }
            )
        }
    }

    if (showDatePicker && enabled) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateInputChange(utcMillisToIsoDate(datePickerState.selectedDateMillis))
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.todo_editor_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.todo_editor_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
internal fun TodoEditorDueTimeSelector(
    dueTimeInput: String,
    onDueTimeInputChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        onClick = {
            val minutes = dueTimeTextToMinutes(dueTimeInput)
            val initialHour = minutes?.div(60) ?: 9
            val initialMinute = minutes?.rem(60) ?: 0
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onDueTimeInputChange(minutesToDueTimeText(hour * 60 + minute))
                },
                initialHour,
                initialMinute,
                true
            ).show()
        },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) Color(0xFFEBEDF4) else Color(0xFFE2E5EC)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = Color(0xFF5C6170)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = if (dueTimeInput.isBlank()) {
                    stringResource(R.string.todo_editor_select_due_time)
                } else {
                    dueTimeInput
                },
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !enabled -> Color(0xFF7A808C)
                    dueTimeInput.isBlank() -> Color(0xFF8E94A3)
                    else -> Color(0xFF2F3441)
                }
            )
        }
    }
}
