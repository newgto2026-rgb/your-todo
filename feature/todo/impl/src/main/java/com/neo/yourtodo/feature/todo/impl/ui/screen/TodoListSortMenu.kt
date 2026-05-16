package com.neo.yourtodo.feature.todo.impl.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoSortOption
import com.neo.yourtodo.feature.todo.impl.R

@Composable
internal fun TodoSortMenu(
    selectedSortOption: TodoSortOption,
    onSortOptionSelected: (TodoSortOption) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedLabel = stringResource(selectedSortOption.labelRes())

    Box(modifier = modifier) {
        Surface(
            onClick = { isExpanded = true },
            modifier = Modifier
                .height(34.dp)
                .testTag("todo_sort_menu_button"),
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Color(0xFFE0E6F1))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (showLabel) 10.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = selectedLabel,
                    tint = Color(0xFF5F5391),
                    modifier = Modifier.size(18.dp)
                )
                if (showLabel) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF5F5391)
                    )
                }
            }
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color(0xFFFAFBFE),
            shadowElevation = 8.dp
        ) {
            TodoSortOption.entries.forEach { option ->
                val isSelected = option == selectedSortOption
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = stringResource(option.labelRes()),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF303440)
                            )
                            Text(
                                text = stringResource(option.descriptionRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7A8595)
                            )
                        }
                    },
                    leadingIcon = {
                        SortOptionDot(color = option.accentColor())
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF5F5391)
                            )
                        }
                    },
                    onClick = {
                        isExpanded = false
                        onSortOptionSelected(option)
                    },
                    modifier = Modifier
                        .background(if (isSelected) Color(0xFFF0F3FF) else Color.Transparent)
                        .testTag(option.testTag())
                )
            }
        }
    }
}

@Composable
private fun SortOptionDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@StringRes
private fun TodoSortOption.labelRes(): Int = when (this) {
    TodoSortOption.DEFAULT -> R.string.todo_sort_default
    TodoSortOption.DUE_DATE -> R.string.todo_sort_due_date
    TodoSortOption.PRIORITY -> R.string.todo_sort_priority
    TodoSortOption.FRIEND -> R.string.todo_sort_friend
}

private fun TodoSortOption.descriptionRes(): Int = when (this) {
    TodoSortOption.DEFAULT -> R.string.todo_sort_default_description
    TodoSortOption.DUE_DATE -> R.string.todo_sort_due_date_description
    TodoSortOption.PRIORITY -> R.string.todo_sort_priority_description
    TodoSortOption.FRIEND -> R.string.todo_sort_friend_description
}

private fun TodoSortOption.accentColor(): Color = when (this) {
    TodoSortOption.DEFAULT -> Color(0xFF8A93A5)
    TodoSortOption.DUE_DATE -> Color(0xFF4B83C5)
    TodoSortOption.PRIORITY -> Color(0xFFC76B7D)
    TodoSortOption.FRIEND -> Color(0xFF6FA58C)
}

@StringRes
internal fun TodoSortOption.subtitleRes(): Int = when (this) {
    TodoSortOption.DEFAULT -> R.string.todo_header_all_subtitle
    TodoSortOption.DUE_DATE -> R.string.todo_header_all_subtitle_due_date
    TodoSortOption.PRIORITY -> R.string.todo_header_all_subtitle_priority
    TodoSortOption.FRIEND -> R.string.todo_header_all_subtitle_friend
}

private fun TodoSortOption.testTag(): String = when (this) {
    TodoSortOption.DEFAULT -> "todo_sort_option_default"
    TodoSortOption.DUE_DATE -> "todo_sort_option_due_date"
    TodoSortOption.PRIORITY -> "todo_sort_option_priority"
    TodoSortOption.FRIEND -> "todo_sort_option_friend"
}
