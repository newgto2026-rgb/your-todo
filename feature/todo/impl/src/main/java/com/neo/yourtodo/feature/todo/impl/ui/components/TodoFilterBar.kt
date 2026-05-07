package com.neo.yourtodo.feature.todo.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.feature.todo.impl.R

@Composable
internal fun BottomFilterBar(
    selectedFilter: TodoFilter,
    onFilterSelected: (TodoFilter) -> Unit,
) {
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xFFF0F1F7)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BottomFilterItem(
                selected = selectedFilter == TodoFilter.ALL,
                label = stringResource(R.string.todo_filter_all),
                icon = Icons.Default.GridView,
                onClick = { onFilterSelected(TodoFilter.ALL) }
            )
            BottomFilterItem(
                selected = selectedFilter == TodoFilter.TODAY,
                label = stringResource(R.string.todo_filter_today),
                icon = Icons.Default.CalendarMonth,
                onClick = { onFilterSelected(TodoFilter.TODAY) }
            )
            BottomFilterItem(
                selected = selectedFilter == TodoFilter.COMPLETED,
                label = stringResource(R.string.todo_filter_completed),
                icon = Icons.Default.CheckCircle,
                onClick = { onFilterSelected(TodoFilter.COMPLETED) }
            )
        }
    }
}

@Composable
private fun BottomFilterItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .background(if (selected) Color(0xFF4A6697) else Color.Transparent, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) Color.White else Color(0xFF8A8F9D)
        )
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF8A8F9D),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        )
    }
}
