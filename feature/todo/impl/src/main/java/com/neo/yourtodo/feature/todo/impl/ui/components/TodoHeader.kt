package com.neo.yourtodo.feature.todo.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.ui.YourTodoBrandHeader
import com.neo.yourtodo.feature.todo.impl.R

@Composable
internal fun AppHeader() {
    YourTodoBrandHeader(
        wordmarkContentDescription = stringResource(R.string.todo_app_header_title),
        profileContentDescription = stringResource(R.string.todo_header_profile_icon)
    )
}

@Composable
internal fun HeaderSummary(
    title: String,
    subtitle: String,
    selectedFilter: TodoFilter,
    completionProgress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.94f),
                        Color(0xFFF5F8FF).copy(alpha = 0.92f),
                        Color(0xFFEFF6FF).copy(alpha = 0.94f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF3C5B8D)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF6E7480)
                )
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White
            ) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(30.dp),
                    contentAlignment = Alignment.Center
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
    }
}
