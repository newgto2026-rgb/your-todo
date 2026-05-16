package com.neo.yourtodo.feature.todo.impl.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.ui.TodoListAction
import com.neo.yourtodo.feature.todo.impl.ui.TodoListUiState

@Composable
internal fun AddTodoFloatingMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onManualAdd: () -> Unit,
    onAiAdd: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (expanded) {
            AddTodoMenuAction(
                label = stringResource(R.string.todo_add_manual),
                icon = Icons.Default.Edit,
                onClick = onManualAdd,
                modifier = Modifier.testTag("add_manual_action")
            )
            AddTodoMenuAction(
                label = stringResource(R.string.todo_add_ai),
                icon = Icons.Default.AutoAwesome,
                onClick = onAiAdd,
                modifier = Modifier.testTag("add_ai_action")
            )
        }
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = Color(0xFF676CB4),
            contentColor = Color.White,
            modifier = Modifier
                .size(58.dp)
                .testTag("add_fab")
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun AddTodoMenuAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFFE0E5F0))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF5F5391),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF384052)
            )
        }
    }
}

@Composable
internal fun QuickAddLauncher(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .testTag("quick_add_open"),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color(0xFFD4DEEC))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE5EDF8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF4A6697),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = stringResource(R.string.todo_quick_add_open),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF51607A)
            )
        }
    }
}

@Composable
internal fun QuickAddSlot(
    uiState: TodoListUiState,
    onAction: (TodoListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val placeholderRes = when (uiState.selectedFilter) {
        TodoFilter.TODAY -> R.string.todo_quick_add_placeholder_today
        else -> R.string.todo_quick_add_placeholder_all
    }
    val errorText = uiState.quickAddErrorMessageRes?.let { stringResource(it) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("quick_add_slot"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.quickAddTitle,
                onValueChange = { onAction(TodoListAction.OnQuickAddTitleChange(it)) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("quick_add_title_input"),
                placeholder = { Text(stringResource(placeholderRes)) },
                isError = uiState.quickAddErrorMessageRes != null,
                supportingText = errorText?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { onAction(TodoListAction.OnQuickAddSubmit) }
                )
            )
            IconButton(
                onClick = { onAction(TodoListAction.OnQuickAddSubmit) },
                modifier = Modifier.testTag("quick_add_submit")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.todo_quick_add_submit)
                )
            }
            IconButton(
                onClick = { onAction(TodoListAction.OnQuickAddDismiss) },
                modifier = Modifier.testTag("quick_add_close")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.todo_quick_add_close)
                )
            }
        }
    }
}
