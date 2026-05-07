package com.neo.yourtodo.feature.calendar.widget

import android.content.Context
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasNoClickAction
import androidx.glance.testing.unit.assertHasTextEqualTo
import androidx.glance.testing.unit.hasTestTag
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CalendarMonthWidgetContentTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun content_rendersCalendarState() = runGlanceAppWidgetUnitTest {
        val selectedDate = LocalDate.of(2026, 5, 7)

        setContext(context)
        setAppWidgetSize(CalendarMonthWidgetSizes.Compact)
        provideComposable {
            CalendarMonthWidgetContent(
                state = CalendarMonthWidgetState(
                    monthLabel = "2026 May",
                    weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S"),
                    weeks = listOf(
                        listOf(
                            CalendarMonthWidgetDay(
                                date = selectedDate,
                                dayLabel = "7",
                                taskCountLabel = "3",
                                isCurrentMonth = true,
                                isToday = true
                            )
                        )
                    ),
                    isError = false
                )
            )
        }

        onNode(hasTestTag(CalendarMonthWidgetTestTags.Root)).assertExists()
        onNode(hasTestTag(CalendarMonthWidgetTestTags.MonthLabel))
            .assertHasTextEqualTo("2026 May")
        onNode(hasTestTag(CalendarMonthWidgetTestTags.dayLabel(selectedDate.toString())))
            .assertHasTextEqualTo("7")
        onNode(hasTestTag(CalendarMonthWidgetTestTags.dayTaskCount(selectedDate.toString())))
            .assertHasTextEqualTo("3")
        onNode(hasTestTag(CalendarMonthWidgetTestTags.dayTodoChip(selectedDate.toString(), 0)))
            .assertDoesNotExist()
    }

    @Test
    fun content_expandedStateRendersTodoChips() = runGlanceAppWidgetUnitTest {
        val selectedDate = LocalDate.of(2026, 5, 7)

        setContext(context)
        setAppWidgetSize(CalendarMonthWidgetSizes.Expanded)
        provideComposable {
            CalendarMonthWidgetContent(
                state = CalendarMonthWidgetState(
                    monthLabel = "2026 May",
                    weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S"),
                    weeks = listOf(
                        listOf(
                            CalendarMonthWidgetDay(
                                date = selectedDate,
                                dayLabel = "7",
                                taskCountLabel = "4",
                                todoChips = listOf(
                                    CalendarMonthWidgetTodoChip(label = "Breakfast with team"),
                                    CalendarMonthWidgetTodoChip(label = "Finalize presentation"),
                                    CalendarMonthWidgetTodoChip(label = "Project update"),
                                    CalendarMonthWidgetTodoChip(label = "Dinner")
                                ),
                                isCurrentMonth = true,
                                isToday = true
                            )
                        )
                    ),
                    isError = false
                )
            )
        }

        onNode(hasTestTag(CalendarMonthWidgetTestTags.dayTodoChip(selectedDate.toString(), 0)))
            .assertHasTextEqualTo("Breakfast with team")
        onNode(hasTestTag(CalendarMonthWidgetTestTags.dayTodoChip(selectedDate.toString(), 3)))
            .assertHasTextEqualTo("Dinner")
        onNode(hasTestTag(CalendarMonthWidgetTestTags.dayTaskCount(selectedDate.toString())))
            .assertDoesNotExist()
    }

    @Test
    fun content_monthNavigationButtonsRunCallbacks() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(CalendarMonthWidgetSizes.Compact)
        provideComposable {
            CalendarMonthWidgetContent(
                state = CalendarMonthWidgetState(
                    monthLabel = "2026 May",
                    weekdayLabels = emptyList(),
                    weeks = emptyList(),
                    isError = false
                )
            )
        }

        onNode(hasTestTag(CalendarMonthWidgetTestTags.PreviousMonthButton))
            .assertHasRunCallbackClickAction<CalendarMonthWidgetMonthNavigationCallback>(
                actionParametersOf(CalendarMonthWidgetActionParameters.MonthDelta.to(-1))
            )
        onNode(hasTestTag(CalendarMonthWidgetTestTags.NextMonthButton))
            .assertHasRunCallbackClickAction<CalendarMonthWidgetMonthNavigationCallback>(
                actionParametersOf(CalendarMonthWidgetActionParameters.MonthDelta.to(1))
            )
    }

    @Test
    fun content_currentMonthDateStartsCalendarActivity() = runGlanceAppWidgetUnitTest {
        val selectedDate = LocalDate.of(2026, 5, 7)

        setContext(context)
        setAppWidgetSize(CalendarMonthWidgetSizes.Compact)
        provideComposable {
            CalendarMonthWidgetContent(
                state = CalendarMonthWidgetState(
                    monthLabel = "2026 May",
                    weekdayLabels = emptyList(),
                    weeks = listOf(
                        listOf(
                            CalendarMonthWidgetDay(
                                date = selectedDate,
                                dayLabel = "7",
                                taskCountLabel = null,
                                isCurrentMonth = true,
                                isToday = false
                            )
                        )
                    ),
                    isError = false
                )
            )
        }

        onNode(hasTestTag(CalendarMonthWidgetTestTags.day(selectedDate.toString())))
            .assertHasStartActivityClickAction(
                CalendarMonthWidgetIntentFactory.openDateIntent(context, selectedDate)
            )
    }

    @Test
    fun content_adjacentMonthDateDoesNotStartCalendarActivity() = runGlanceAppWidgetUnitTest {
        val adjacentMonthDate = LocalDate.of(2026, 4, 30)

        setContext(context)
        setAppWidgetSize(CalendarMonthWidgetSizes.Expanded)
        provideComposable {
            CalendarMonthWidgetContent(
                state = CalendarMonthWidgetState(
                    monthLabel = "2026 May",
                    weekdayLabels = emptyList(),
                    weeks = listOf(
                        listOf(
                            CalendarMonthWidgetDay(
                                date = adjacentMonthDate,
                                dayLabel = "30",
                                taskCountLabel = "2",
                                todoChips = listOf(CalendarMonthWidgetTodoChip(label = "Hidden")),
                                isCurrentMonth = false,
                                isToday = false
                            )
                        )
                    ),
                    isError = false
                )
            )
        }

        onNode(hasTestTag(CalendarMonthWidgetTestTags.day(adjacentMonthDate.toString())))
            .assertHasNoClickAction()
        onNode(hasTestTag(CalendarMonthWidgetTestTags.dayTodoChip(adjacentMonthDate.toString(), 0)))
            .assertDoesNotExist()
    }

    @Test
    fun content_errorStateShowsFallbackCopy() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(CalendarMonthWidgetSizes.Compact)
        provideComposable {
            CalendarMonthWidgetContent(
                state = CalendarMonthWidgetState(
                    monthLabel = "",
                    weekdayLabels = emptyList(),
                    weeks = emptyList(),
                    isError = true
                )
            )
        }

        onNode(hasTestTag(CalendarMonthWidgetTestTags.MonthLabel))
            .assertHasTextEqualTo(context.getString(R.string.calendar_widget_name))
        onNode(hasTestTag(CalendarMonthWidgetTestTags.ErrorMessage))
            .assertHasTextEqualTo(context.getString(R.string.calendar_widget_error))
    }
}
