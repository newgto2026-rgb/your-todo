package com.neo.yourtodo.feature.todo.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.feature.todo.impl.R

@Composable
internal fun PriorityFilterBar(
    selectedPriorityFilter: TodoPriorityFilter,
    onPrioritySelected: (TodoPriorityFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("priority_filter_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PriorityFilterChip(
            label = stringResource(R.string.todo_filter_all),
            selected = selectedPriorityFilter == TodoPriorityFilter.ALL,
            color = Color(0xFF7087B5),
            onClick = { onPrioritySelected(TodoPriorityFilter.ALL) }
        )
        PriorityFilterChip(
            label = stringResource(R.string.todo_priority_low),
            selected = selectedPriorityFilter == TodoPriorityFilter.LOW,
            color = Color(0xFF6E8E72),
            onClick = { onPrioritySelected(TodoPriorityFilter.LOW) }
        )
        PriorityFilterChip(
            label = stringResource(R.string.todo_priority_medium),
            selected = selectedPriorityFilter == TodoPriorityFilter.MEDIUM,
            color = Color(0xFF8B7A4E),
            onClick = { onPrioritySelected(TodoPriorityFilter.MEDIUM) }
        )
        PriorityFilterChip(
            label = stringResource(R.string.todo_priority_high),
            selected = selectedPriorityFilter == TodoPriorityFilter.HIGH,
            color = Color(0xFF9B4B4B),
            onClick = { onPrioritySelected(TodoPriorityFilter.HIGH) }
        )
    }
}

@Composable
private fun PriorityFilterChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) color.copy(alpha = 0.18f) else Color(0xFFE8EBF3),
        onClick = onClick
    ) {
        Text(
            text = label,
            color = if (selected) color else Color(0xFF6C7382),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .testTag("priority_filter_chip_$label")
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
