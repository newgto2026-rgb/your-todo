package com.neo.yourtodo.feature.calendar.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

internal object CalendarMonthWidgetSizes {
    val Compact = DpSize(width = 180.dp, height = 180.dp)
    val Expanded = DpSize(width = 250.dp, height = 250.dp)
    val All = setOf(Compact, Expanded)
}

internal enum class CalendarMonthWidgetLayout {
    Compact,
    Expanded;

    val contentPadding
        get() = when (this) {
            Compact -> 8.dp
            Expanded -> 6.dp
        }

    val headerSpacing
        get() = when (this) {
            Compact -> 4.dp
            Expanded -> 3.dp
        }

    val brandLogoHeight
        get() = when (this) {
            Compact -> 0.dp
            Expanded -> 17.dp
        }

    val brandLogoWidth
        get() = when (this) {
            Compact -> 0.dp
            Expanded -> 65.dp
        }

    val brandLogoSpacing
        get() = when (this) {
            Compact -> 0.dp
            Expanded -> 2.dp
        }

    val weekdaySpacing
        get() = when (this) {
            Compact -> 3.dp
            Expanded -> 2.dp
        }

    val titleFontSize
        get() = when (this) {
            Compact -> 15
            Expanded -> 17
        }

    val navigationButtonSize
        get() = when (this) {
            Compact -> 28.dp
            Expanded -> 28.dp
        }

    val navigationFontSize
        get() = when (this) {
            Compact -> 14
            Expanded -> 15
        }

    val weekdayFontSize
        get() = when (this) {
            Compact -> 10
            Expanded -> 11
        }

    val dayFontSize
        get() = when (this) {
            Compact -> 15
            Expanded -> 15
        }

    val taskCountFontSize
        get() = when (this) {
            Compact -> 11
            Expanded -> 10
        }

    val taskCountPlaceholderSize
        get() = when (this) {
            Compact -> 11.dp
            Expanded -> 11.dp
        }

    val todoChipFontSize
        get() = when (this) {
            Compact -> 11
            Expanded -> 10
        }

    val todoChipHorizontalPadding
        get() = when (this) {
            Compact -> 0.dp
            Expanded -> 0.dp
        }

    val cellHorizontalGap
        get() = when (this) {
            Compact -> 0.dp
            Expanded -> 0.dp
        }

    val cellVerticalGap
        get() = when (this) {
            Compact -> 1.dp
            Expanded -> 1.dp
        }

    val expandedCellVerticalPadding
        get() = when (this) {
            Compact -> 3.dp
            Expanded -> 1.dp
        }

    val compactCellVerticalPadding
        get() = when (this) {
            Compact -> 2.dp
            Expanded -> 2.dp
        }

    companion object {
        fun fromSize(size: DpSize): CalendarMonthWidgetLayout =
            if (size.width >= ExpandedMinWidth && size.height >= ExpandedMinHeight) {
                Expanded
            } else {
                Compact
            }

        private val ExpandedMinWidth = 240.dp
        private val ExpandedMinHeight = 240.dp
    }
}

internal object CalendarMonthWidgetTestTags {
    const val Root = "calendar_widget_root"
    const val BrandLogo = "calendar_widget_brand_logo"
    const val MonthLabel = "calendar_widget_month_label"
    const val PreviousMonthButton = "calendar_widget_previous_month_button"
    const val NextMonthButton = "calendar_widget_next_month_button"
    const val ErrorMessage = "calendar_widget_error_message"

    fun day(date: String): String = "calendar_widget_day_$date"
    fun dayLabel(date: String): String = "calendar_widget_day_label_$date"
    fun dayTaskCount(date: String): String = "calendar_widget_day_task_count_$date"
    fun dayTodoChip(date: String, index: Int): String = "calendar_widget_day_todo_chip_${date}_$index"
}
