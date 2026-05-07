package com.neo.yourtodo.feature.calendar.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Test

class CalendarMonthWidgetPreferencesTest {

    @Test
    fun displayedMonthOrNull_parsesStoredMonth() {
        val preferences = preferencesOf(
            CalendarMonthWidgetPreferences.DisplayedMonth to "2026-07"
        )

        assertThat(preferences.displayedMonthOrNull()).isEqualTo(YearMonth.of(2026, 7))
    }

    @Test
    fun displayedMonthOrCurrent_fallsBackToCurrentMonthWhenStoredMonthIsInvalid() {
        val preferences = preferencesOf(
            CalendarMonthWidgetPreferences.DisplayedMonth to "not-a-month"
        )

        assertThat(preferences.displayedMonthOrCurrent(fixedClock("2026-05-07T00:00:00Z")))
            .isEqualTo(YearMonth.of(2026, 5))
    }

    @Test
    fun setDisplayedMonth_storesYearMonthAsIsoValue() {
        val preferences = mutablePreferencesOf()

        preferences.setDisplayedMonth(YearMonth.of(2026, 12))

        assertThat(preferences[CalendarMonthWidgetPreferences.DisplayedMonth])
            .isEqualTo("2026-12")
        assertThat(preferences.displayedMonthOrNull()).isEqualTo(YearMonth.of(2026, 12))
    }

    @Test
    fun moveByWidgetMonthDelta_movesAcrossYearBoundary() {
        assertThat(YearMonth.of(2026, 1).moveByWidgetMonthDelta(-1))
            .isEqualTo(YearMonth.of(2025, 12))
        assertThat(YearMonth.of(2026, 12).moveByWidgetMonthDelta(1))
            .isEqualTo(YearMonth.of(2027, 1))
    }

    private fun fixedClock(instant: String): Clock =
        Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"))
}
