package com.neo.yourtodo.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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

@OptIn(ExperimentalFoundationApi::class)
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
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    priorityLabel: String,
    priorityColor: Color,
    toggleTestTag: String? = null,
    sourceText: String? = null,
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
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor,
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (sourceText != null || dueDateText != null || (isReminderEnabled && !reminderText.isNullOrBlank())) {
                Row(
                    modifier = Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sourceText != null) {
                        TodoMetaChip(
                            text = sourceText,
                            contentColor = if (isDone) Color(0xFF3C7766).copy(alpha = 0.5f) else Color(0xFF3C7766),
                            containerColor = if (isDone) Color(0xFFE4ECE8) else Color(0xFFEAF4F0)
                        )
                    }
                    if (dueDateText != null) {
                        TodoMetaChip(
                            text = dueDateText,
                            contentColor = subtitleColor,
                            containerColor = if (isDone) Color(0xFFE8EBF1) else Color(0xFFF0F3F8)
                        )
                    }
                    if (isReminderEnabled && !reminderText.isNullOrBlank()) {
                        TodoMetaChip(
                            text = reminderText,
                            contentColor = subtitleColor,
                            containerColor = if (isDone) Color(0xFFE8EBF1) else Color(0xFFF0F3F8),
                            content = {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = subtitleColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(priorityColor.copy(alpha = if (isDone) 0.34f else 0.72f))
                .testTag("todo_row_priority_$priorityLabel")
        )

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

@Composable
private fun TodoMetaChip(
    text: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(PaddingValues(horizontal = 7.dp, vertical = 3.dp)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (content != null) {
                content()
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
