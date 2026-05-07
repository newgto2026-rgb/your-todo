package com.neo.yourtodo.feature.calendar.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalendarMonthWidgetLayoutTest {
    @Test
    fun compactLayout_usesReadableCalendarTextWithMinimalHorizontalGap() {
        val layout = CalendarMonthWidgetLayout.Compact

        assertThat(layout.dayFontSize).isAtLeast(15)
        assertThat(layout.taskCountFontSize).isAtLeast(11)
        assertThat(layout.weekdayFontSize).isAtLeast(10)
        assertThat(layout.cellHorizontalGap.value).isEqualTo(0f)
    }

    @Test
    fun expandedLayout_usesReadablePreviewTextAndCapsVisibleWeeks() {
        val layout = CalendarMonthWidgetLayout.Expanded

        assertThat(layout.dayFontSize).isAtLeast(15)
        assertThat(layout.brandLogoHeight.value).isAtLeast(layout.titleFontSize.toFloat())
        assertThat(layout.todoChipFontSize).isAtLeast(10)
        assertThat(layout.todoChipHorizontalPadding.value).isEqualTo(0f)
        assertThat(layout.cellHorizontalGap.value).isEqualTo(0f)
    }
}
