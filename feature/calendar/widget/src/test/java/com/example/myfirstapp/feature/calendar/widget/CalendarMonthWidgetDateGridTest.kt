package com.example.myfirstapp.feature.calendar.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class CalendarMonthWidgetDateGridTest {

    @Test
    fun buildCalendarMonthWidgetDateGrid_buildsFullWeeks() {
        val weeks = buildCalendarMonthWidgetDateGrid(
            yearMonth = YearMonth.of(2026, 5),
            locale = Locale.US
        )

        assertThat(weeks).hasSize(6)
        assertThat(weeks.flatten()).hasSize(42)
        assertThat(weeks.first().first().date).isEqualTo(LocalDate.of(2026, 4, 26))
        assertThat(weeks.flatten().filter { it.isCurrentMonth }).hasSize(31)
    }

    @Test
    fun buildCalendarMonthWidgetDateGrid_respectsLocaleFirstDayOfWeek() {
        val usWeeks = buildCalendarMonthWidgetDateGrid(
            yearMonth = YearMonth.of(2026, 5),
            locale = Locale.US
        )
        val ukWeeks = buildCalendarMonthWidgetDateGrid(
            yearMonth = YearMonth.of(2026, 5),
            locale = Locale.UK
        )

        assertThat(usWeeks.first().first().date).isEqualTo(LocalDate.of(2026, 4, 26))
        assertThat(ukWeeks.first().first().date).isEqualTo(LocalDate.of(2026, 4, 27))
    }

    @Test
    fun buildCalendarMonthWidgetDateGrid_handlesLeapFebruary() {
        val days = buildCalendarMonthWidgetDateGrid(
            yearMonth = YearMonth.of(2024, 2),
            locale = Locale.US
        ).flatten()

        assertThat(days.filter { it.isCurrentMonth }).hasSize(29)
        assertThat(days.map { it.date }).contains(LocalDate.of(2024, 2, 29))
    }
}
