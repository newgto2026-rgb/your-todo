package com.neo.yourtodo.feature.todo.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel

@Composable
internal fun TodoListItems(
    uiState: TodoListUiState,
    rowCompletedText: String,
    rowTodayText: String,
    dueDateFormat: String,
    listState: LazyListState,
    onAction: (TodoListAction) -> Unit,
    onEditRequested: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("todo_list"),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.selectedFilter == TodoFilter.TODAY) {
            todayPlannerSections(uiState.items).forEach { section ->
                if (section.items.isNotEmpty()) {
                    item(key = "section_${section.titleRes}") {
                        TodoListSectionHeader(text = stringResource(section.titleRes))
                    }
                    items(items = section.items, key = { item -> item.id }) { item ->
                        TodoListRowItem(
                            item = item,
                            rowCompletedText = rowCompletedText,
                            rowTodayText = rowTodayText,
                            dueDateFormat = dueDateFormat,
                            onAction = onAction,
                            onEditRequested = onEditRequested,
                            showQuickActions = true
                        )
                    }
                }
            }
        } else if (uiState.sections.isNotEmpty()) {
            uiState.sections.forEach { section ->
                item(key = "section_${section.key}") {
                    TodoListSectionHeader(
                        text = section.key.label(dueDateFormat = dueDateFormat)
                    )
                }
                items(items = section.items, key = { item -> item.id }) { item ->
                    TodoListRowItem(
                        item = item,
                        rowCompletedText = rowCompletedText,
                        rowTodayText = rowTodayText,
                        dueDateFormat = dueDateFormat,
                        onAction = onAction,
                        onEditRequested = onEditRequested,
                        showQuickActions = false
                    )
                }
            }
        } else {
            items(items = uiState.items, key = { item -> item.id }) { item ->
                TodoListRowItem(
                    item = item,
                    rowCompletedText = rowCompletedText,
                    rowTodayText = rowTodayText,
                    dueDateFormat = dueDateFormat,
                    onAction = onAction,
                    onEditRequested = onEditRequested,
                    showQuickActions = false
                )
            }
        }
    }
}

@Composable
private fun TodoListRowItem(
    item: TodoItemUiModel,
    rowCompletedText: String,
    rowTodayText: String,
    dueDateFormat: String,
    onAction: (TodoListAction) -> Unit,
    onEditRequested: (Long) -> Unit,
    showQuickActions: Boolean
) {
    TodoPlannerRow(
        item = item,
        rowCompletedText = rowCompletedText,
        rowTodayText = rowTodayText,
        dueDateFormat = dueDateFormat,
        onAction = onAction,
        onEditRequested = onEditRequested,
        onDeleteRequest = {
            item.assignedTodoId?.let {
                onAction(TodoListAction.OnAssignedDeleteRequest(it))
            } ?: onAction(TodoListAction.OnDeleteRequest(item.id))
        },
        showQuickActions = showQuickActions
    )
}
