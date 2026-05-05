package com.example.myfirstapp.feature.todo.impl.ui.editor

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.myfirstapp.feature.todo.impl.ui.EditTodoBottomSheet

private const val TAG = "NavScopeTrace"

@Composable
fun TodoEditorRouteScreen(
    initialTodoId: Long?,
    initialDueDate: String?,
    onExit: () -> Unit,
    viewModel: TodoEditorViewModel = hiltViewModel()
) {
    val owner = LocalViewModelStoreOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(owner, viewModel) {
        val ownerName = owner?.let { it::class.java.simpleName } ?: "null"
        val ownerId = owner?.let { System.identityHashCode(it) } ?: -1
        Log.d(
            TAG,
            "TodoEditorRouteScreen enter owner=$ownerName@$ownerId vm=${System.identityHashCode(viewModel)} todoId=$initialTodoId dueDate=$initialDueDate"
        )
        onDispose {
            Log.d(
                TAG,
                "TodoEditorRouteScreen dispose owner=$ownerName@$ownerId vm=${System.identityHashCode(viewModel)}"
            )
        }
    }

    LaunchedEffect(initialTodoId, initialDueDate) {
        Log.d(TAG, "initialize todoId=$initialTodoId dueDate=$initialDueDate vm=${System.identityHashCode(viewModel)}")
        viewModel.initialize(todoId = initialTodoId, dueDate = initialDueDate)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                TodoEditorSideEffect.Exit -> {
                    Log.d(TAG, "sideEffect Exit vm=${System.identityHashCode(viewModel)}")
                    onExit()
                }
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
