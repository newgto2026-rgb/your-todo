package com.neo.yourtodo.feature.todo.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.feature.todo.impl.R

@Composable
internal fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.todo_app_header_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF2D3240)
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F3A56)),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.todo_header_profile_icon), color = Color.White)
        }
    }
}

@Composable
internal fun HeaderSummary(
    title: String,
    subtitle: String,
    selectedFilter: TodoFilter,
    completionProgress: Float
) {
    Text(
        text = title,
        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
        color = Color(0xFF3C5B8D)
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        color = Color(0xFF6E7480)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFE6E8F1)
        )
        Box(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(34.dp)
        ) {
            if (selectedFilter == TodoFilter.COMPLETED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFFE8ECF6)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.todo_header_completed_symbol),
                        color = Color(0xFF5F78A6),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                CircularProgressIndicator(
                    progress = { completionProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF74659E),
                    trackColor = Color(0xFFE1E4EE),
                    strokeWidth = 4.dp
                )
            }
        }
    }
}
