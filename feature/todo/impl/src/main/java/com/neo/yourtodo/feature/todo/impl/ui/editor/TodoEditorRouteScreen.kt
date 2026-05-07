package com.neo.yourtodo.feature.todo.impl.ui.editor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.yourtodo.feature.todo.impl.ui.EditTodoBottomSheet

@Composable
fun TodoEditorRouteScreen(
    initialTodoId: Long?,
    initialDueDate: String?,
    onExit: () -> Unit,
    viewModel: TodoEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialTodoId, initialDueDate) {
        viewModel.initialize(todoId = initialTodoId, dueDate = initialDueDate)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                TodoEditorSideEffect.Exit -> onExit()
            }
        }
    }

    EditTodoBottomSheet(
        sheetTitle = stringResource(uiState.sheetTitleRes),
        title = uiState.title,
        dueDateInput = uiState.dueDateInput,
        dueTimeInput = uiState.dueTimeInput,
        reminderEnabled = uiState.reminderEnabled,
        reminderLeadMinutes = uiState.reminderLeadMinutes,
        selectedPriority = uiState.priority,
        errorMessageRes = uiState.errorMessageRes,
        onTitleChange = viewModel::onTitleChange,
        onDateInputChange = viewModel::onDateInputChange,
        onDueTimeInputChange = viewModel::onDueTimeInputChange,
        onReminderEnabledChange = viewModel::onReminderEnabledChange,
        onReminderLeadMinutesChange = viewModel::onReminderLeadMinutesChange,
        onPrioritySelected = viewModel::onPrioritySelected,
        onDismiss = onExit,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        showDelete = uiState.showDelete
    )
}
