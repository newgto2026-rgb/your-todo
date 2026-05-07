package com.neo.yourtodo.feature.calendar.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

internal object CalendarMonthWidgetPreferences {
    val DisplayedMonth: Preferences.Key<String> =
        stringPreferencesKey("calendar_month_widget_displayed_month")
}

internal fun Preferences.displayedMonthOrNull(): YearMonth? =
    this[CalendarMonthWidgetPreferences.DisplayedMonth]?.toYearMonthOrNull()

internal fun Preferences.displayedMonthOrCurrent(clock: Clock): YearMonth =
    displayedMonthOrNull() ?: YearMonth.from(LocalDate.now(clock))

internal fun MutablePreferences.setDisplayedMonth(yearMonth: YearMonth) {
    this[CalendarMonthWidgetPreferences.DisplayedMonth] = yearMonth.toString()
}

internal fun YearMonth.moveByWidgetMonthDelta(monthDelta: Int): YearMonth =
    plusMonths(monthDelta.toLong())

private fun String.toYearMonthOrNull(): YearMonth? =
    runCatching { YearMonth.parse(this) }.getOrNull()
