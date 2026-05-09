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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.feature.todo.impl.R

@Composable
internal fun TodoEditorPrioritySection(
    selectedPriority: TodoPriority,
    onPrioritySelected: (TodoPriority) -> Unit,
    enabled: Boolean = true
) {
    Text(
        text = stringResource(R.string.todo_editor_priority_label),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = Color(0xFF7A7F8C),
        modifier = Modifier.testTag("priority_section")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PriorityOptionChip(
            label = stringResource(R.string.todo_priority_low),
            selected = selectedPriority == TodoPriority.LOW,
            color = Color(0xFF6FA58C),
            testTag = "priority_low_option",
            enabled = enabled,
            onClick = { onPrioritySelected(TodoPriority.LOW) }
        )
        PriorityOptionChip(
            label = stringResource(R.string.todo_priority_medium),
            selected = selectedPriority == TodoPriority.MEDIUM,
            color = Color(0xFF6F86C9),
            testTag = "priority_medium_option",
            enabled = enabled,
            onClick = { onPrioritySelected(TodoPriority.MEDIUM) }
        )
        PriorityOptionChip(
            label = stringResource(R.string.todo_priority_high),
            selected = selectedPriority == TodoPriority.HIGH,
            color = Color(0xFFC76B7D),
            testTag = "priority_high_option",
            enabled = enabled,
            onClick = { onPrioritySelected(TodoPriority.HIGH) }
        )
    }
}

@Composable
private fun PriorityOptionChip(
    label: String,
    selected: Boolean,
    color: Color,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            !enabled -> Color(0xFFE1E4EC)
            selected -> color.copy(alpha = 0.2f)
            else -> Color(0xFFE8EBF3)
        },
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = when {
                !enabled -> Color(0xFF8A909C)
                selected -> color
                else -> Color(0xFF6C7382)
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}
