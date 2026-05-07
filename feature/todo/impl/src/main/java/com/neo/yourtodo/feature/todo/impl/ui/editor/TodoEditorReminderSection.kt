package com.neo.yourtodo.feature.todo.impl.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.feature.todo.impl.R

@Composable
internal fun TodoEditorReminderSection(
    reminderEnabled: Boolean,
    reminderLeadMinutes: Int,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderLeadMinutesChange: (Int) -> Unit
) {
    Text(
        text = stringResource(R.string.todo_editor_reminder_label),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = Color(0xFF7A7F8C)
    )
    Spacer(Modifier.height(10.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF0F1F5)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.todo_editor_enable_reminder),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = onReminderEnabledChange
                )
            }
            Spacer(Modifier.height(8.dp))
            val presets = TodoReminderLeadPreset.values().toList()
            val firstRow = presets.take(3)
            val secondRow = presets.drop(3)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                firstRow.forEach { preset ->
                    ReminderPresetChip(
                        label = stringResource(preset.labelRes),
                        selected = reminderLeadMinutes == preset.minutes,
                        enabled = reminderEnabled,
                        onClick = { onReminderLeadMinutesChange(preset.minutes) }
                    )
                }
            }

            if (secondRow.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    secondRow.forEach { preset ->
                        ReminderPresetChip(
                            label = stringResource(preset.labelRes),
                            selected = reminderLeadMinutes == preset.minutes,
                            enabled = reminderEnabled,
                            onClick = { onReminderLeadMinutesChange(preset.minutes) }
                        )
                    }
                }
            }
        }
    }
}

private enum class TodoReminderLeadPreset(
    val minutes: Int,
    @StringRes val labelRes: Int
) {
    AT_TIME(0, R.string.todo_reminder_lead_at_time),
    FIVE_MIN(5, R.string.todo_reminder_lead_5m),
    TEN_MIN(10, R.string.todo_reminder_lead_10m),
    THIRTY_MIN(30, R.string.todo_reminder_lead_30m),
    SIXTY_MIN(60, R.string.todo_reminder_lead_60m)
}

@Composable
private fun ReminderPresetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            !enabled -> Color(0xFFE8EBF3)
            selected -> Color(0xFF5F78A6).copy(alpha = 0.18f)
            else -> Color(0xFFE8EBF3)
        },
        onClick = onClick,
        enabled = enabled
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected && enabled) Color(0xFF4A6697) else Color(0xFF6C7382),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
