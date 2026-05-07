package com.neo.yourtodo.feature.calendar.impl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.feature.calendar.impl.R
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarDayUiModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

@Composable
internal fun CalendarMonthGrid(
    days: List<CalendarDayUiModel>,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        WeekdayHeaderRow()
        Spacer(modifier = Modifier.height(8.dp))

        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        onClick = onDateClick
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val locale = Locale.getDefault()
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val dayOrder = List(7) { firstDayOfWeek.plus(it.toLong()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        dayOrder.forEach { dayOfWeek ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RowScope.CalendarDayCell(
    day: CalendarDayUiModel,
    onClick: (LocalDate) -> Unit
) {
    val date = day.date ?: run {
        Spacer(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        )
        return
    }

    val totalCount = day.indicatorCount + day.overflowCount
    val isInactiveMonth = !day.isCurrentMonth
    val hasItems = !isInactiveMonth && totalCount > 0
    val textColor = when {
        day.isSelected -> MaterialTheme.colorScheme.onPrimary
        isInactiveMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
        day.isToday && !isInactiveMonth -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dateFontWeight = if (day.isSelected || (day.isToday && !isInactiveMonth)) {
        FontWeight.SemiBold
    } else {
        FontWeight.Medium
    }
    val indicatorColor = if (day.isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    val a11yParts = buildList {
        add(date.format(DateTimeFormatter.ofPattern("yyyy MMMM d EEEE", Locale.getDefault())))
        add(
            pluralStringResource(
                id = R.plurals.calendar_a11y_todo_count,
                count = totalCount,
                totalCount
            )
        )
        if (day.isSelected) add(stringResource(R.string.calendar_a11y_selected))
        if (day.isToday) add(stringResource(R.string.calendar_a11y_today))
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("calendar_day_$date")
            .clickable(enabled = day.isCurrentMonth) { onClick(date) }
            .semantics { contentDescription = a11yParts.joinToString(separator = ", ") }
            .padding(vertical = 5.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .then(
                    if (day.isToday && !isInactiveMonth && !day.isSelected) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (day.isSelected) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
            }
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = dateFontWeight),
                color = textColor
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier.height(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasItems) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            color = indicatorColor,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

private fun DayOfWeek.plus(days: Long): DayOfWeek {
    val normalized = ((value - 1 + days) % 7 + 7) % 7
    return DayOfWeek.of(normalized.toInt() + 1)
}
