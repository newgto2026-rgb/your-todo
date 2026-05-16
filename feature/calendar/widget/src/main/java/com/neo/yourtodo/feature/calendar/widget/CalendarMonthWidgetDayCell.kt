package com.neo.yourtodo.feature.calendar.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

@Composable
internal fun CalendarDayCell(
    day: CalendarMonthWidgetDay,
    layout: CalendarMonthWidgetLayout,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    val paddedModifier = modifier
        .fillMaxHeight()
        .padding(horizontal = layout.cellHorizontalGap, vertical = layout.cellVerticalGap)
    val baseModifier = if (day.isCurrentMonth) {
        paddedModifier
            .semantics { testTag = CalendarMonthWidgetTestTags.day(day.date.toString()) }
            .clickable(
                actionStartActivity(
                    CalendarMonthWidgetIntentFactory.openDateIntent(context, day.date)
                )
            )
    } else {
        paddedModifier.semantics { testTag = CalendarMonthWidgetTestTags.day(day.date.toString()) }
    }
    val cellModifier = baseModifier
        .background(ColorProvider(day.backgroundColor))
        .cornerRadius(DayCellCornerRadius)
    val textColor = day.textColor

    if (layout == CalendarMonthWidgetLayout.Expanded) {
        ExpandedCalendarDayCell(
            day = day,
            modifier = cellModifier,
            textColor = textColor,
            layout = layout
        )
    } else {
        CompactCalendarDayCell(
            day = day,
            modifier = cellModifier,
            textColor = textColor,
            layout = layout
        )
    }
}

@Composable
private fun CompactCalendarDayCell(
    day: CalendarMonthWidgetDay,
    modifier: GlanceModifier,
    textColor: Color,
    layout: CalendarMonthWidgetLayout
) {
    Column(
        modifier = modifier.padding(vertical = layout.compactCellVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DayLabel(
            day = day,
            textColor = textColor,
            fontSize = layout.dayFontSize
        )
        if (day.taskCountLabel != null && day.isCurrentMonth) {
            val countColor = if (day.isToday) TodayTextColor else CountTextColor
            Text(
                text = day.taskCountLabel,
                modifier = GlanceModifier.semantics {
                    testTag = CalendarMonthWidgetTestTags.dayTaskCount(day.date.toString())
                },
                style = TextStyle(
                    color = ColorProvider(countColor),
                    fontSize = layout.taskCountFontSize.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        } else {
            Spacer(modifier = GlanceModifier.size(layout.taskCountPlaceholderSize))
        }
    }
}

@Composable
private fun ExpandedCalendarDayCell(
    day: CalendarMonthWidgetDay,
    modifier: GlanceModifier,
    textColor: Color,
    layout: CalendarMonthWidgetLayout
) {
    Column(
        modifier = modifier.padding(vertical = layout.expandedCellVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DayLabel(
            day = day,
            textColor = textColor,
            fontSize = layout.dayFontSize
        )

        if (day.isCurrentMonth) {
            day.todoChips.forEachIndexed { index, chip ->
                TodoPreviewChip(
                    date = day.date.toString(),
                    index = index,
                    chip = chip,
                    isToday = day.isToday,
                    layout = layout
                )
            }
        }
    }
}

@Composable
private fun DayLabel(
    day: CalendarMonthWidgetDay,
    textColor: Color,
    fontSize: Int
) {
    Text(
        text = day.dayLabel,
        modifier = GlanceModifier.semantics {
            testTag = CalendarMonthWidgetTestTags.dayLabel(day.date.toString())
        },
        style = TextStyle(
            color = ColorProvider(textColor),
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
        ),
        maxLines = 1
    )
}

@Composable
private fun TodoPreviewChip(
    date: String,
    index: Int,
    chip: CalendarMonthWidgetTodoChip,
    isToday: Boolean,
    layout: CalendarMonthWidgetLayout
) {
    val backgroundColor = chip.backgroundColor(isToday)
    val textColor = chip.textColor(isToday)

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp, vertical = 1.dp)
    ) {
        Text(
            text = chip.label,
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(backgroundColor))
                .cornerRadius(ChipCornerRadius)
                .padding(horizontal = layout.todoChipHorizontalPadding)
                .semantics {
                    testTag = CalendarMonthWidgetTestTags.dayTodoChip(date, index)
                },
            style = TextStyle(
                color = ColorProvider(textColor),
                fontSize = layout.todoChipFontSize.sp,
                textAlign = TextAlign.Start,
                fontWeight = if (chip.isOverflow) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = TodoChipMaxLines
        )
    }
}

private val CalendarMonthWidgetDay.backgroundColor: Color
    get() = when {
        isToday -> TodayBackground
        isCurrentMonth -> DayCellBackground
        else -> AdjacentDayCellBackground
    }

private val CalendarMonthWidgetDay.textColor: Color
    get() = when {
        isToday -> TodayTextColor
        isCurrentMonth -> PrimaryTextColor
        else -> MutedTextColor
    }

private fun CalendarMonthWidgetTodoChip.backgroundColor(isToday: Boolean): Color = when {
    isToday -> TodayChipBackground
    isOverflow -> OverflowChipBackground
    isDone -> DoneChipBackground
    else -> TodoChipBackground
}

private fun CalendarMonthWidgetTodoChip.textColor(isToday: Boolean): Color = when {
    isToday -> TodayChipText
    isOverflow -> OverflowChipText
    isDone -> DoneChipText
    else -> TodoChipText
}
