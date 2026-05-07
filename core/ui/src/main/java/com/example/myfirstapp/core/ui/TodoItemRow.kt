package com.example.myfirstapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TodoItemRow(
    title: String,
    dueDateText: String?,
    reminderText: String?,
    isDone: Boolean,
    isEmphasized: Boolean,
    isReminderEnabled: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    priorityLabel: String,
    priorityColor: Color,
    toggleTestTag: String? = null,
    content: @Composable (() -> Unit)? = null
) {
    val containerColor = when {
        isDone -> Color(0xFFF2F4F8)
        isEmphasized -> Color(0xFFEAF0FA)
        else -> Color.White
    }
    val titleColor = if (isDone) Color(0xFF2D3338).copy(alpha = 0.56f) else Color(0xFF2D3338)
    val subtitleColor = if (isDone) Color(0xFF5A6065).copy(alpha = 0.5f) else Color(0xFF5A6065)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(if (toggleTestTag != null) Modifier.testTag(toggleTestTag) else Modifier)
                .background(if (isDone) Color(0xFFDDE4F4) else Color(0xFFF7F9FD))
                .border(
                    width = 1.2.dp,
                    color = if (isDone) Color(0xFF6E7F9B) else Color(0xFFC8D2E3),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onToggleDone),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF43566F),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = titleColor,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = priorityColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = priorityLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = priorityColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            if (dueDateText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "·", style = MaterialTheme.typography.bodySmall, color = subtitleColor)
                    Spacer(Modifier.size(2.dp))
                    Text(text = dueDateText, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
                    if (isReminderEnabled && !reminderText.isNullOrBlank()) {
                        Spacer(Modifier.size(6.dp))
                        Text(text = "·", style = MaterialTheme.typography.bodySmall, color = subtitleColor)
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = subtitleColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = reminderText,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                }
            } else if (isReminderEnabled && !reminderText.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = subtitleColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = reminderText,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor
                    )
                }
            }
        }

        if (content != null) {
            Box(
                modifier = Modifier.align(Alignment.CenterVertically),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}
