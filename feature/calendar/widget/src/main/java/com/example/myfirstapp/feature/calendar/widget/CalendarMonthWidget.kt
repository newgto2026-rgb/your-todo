package com.example.myfirstapp.feature.calendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

class CalendarMonthWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val presenter = runCatching {
            EntryPointAccessors
                .fromApplication(context, CalendarMonthWidgetEntryPoint::class.java)
                .presenter()
        }.getOrNull()

        provideContent {
            if (presenter == null) {
                CalendarMonthWidgetContent(state = calendarMonthWidgetErrorState())
            } else {
                CalendarMonthWidgetContent(presenter = presenter)
            }
        }
    }
}

@Composable
internal fun CalendarMonthWidgetContent(presenter: CalendarMonthWidgetPresenter) {
    val preferences = currentState<Preferences>()
    val state = runCatching {
        runBlocking {
            presenter.present(displayedMonth = preferences.displayedMonthOrNull())
        }
    }.getOrElse {
        calendarMonthWidgetErrorState()
    }

    CalendarMonthWidgetContent(state = state)
}

@Composable
internal fun CalendarMonthWidgetContent(state: CalendarMonthWidgetState) {
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBackground))
            .padding(12.dp)
            .semantics { testTag = CalendarMonthWidgetTestTags.Root }
    ) {
        CalendarMonthHeader(
            monthLabel = state.monthLabel.ifBlank {
                context.getString(R.string.calendar_widget_name)
            }
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (state.isError) {
            WidgetMessage(text = context.getString(R.string.calendar_widget_error))
            return@Column
        }

        WeekdayHeader(labels = state.weekdayLabels)
        Spacer(modifier = GlanceModifier.height(4.dp))

        state.weeks.forEach { week ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }
    }
}

internal fun calendarMonthWidgetErrorState(): CalendarMonthWidgetState =
    CalendarMonthWidgetState(
        monthLabel = "",
        weekdayLabels = emptyList(),
        weeks = emptyList(),
        isError = true
    )

@Composable
private fun CalendarMonthHeader(monthLabel: String) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthNavigationButton(
            text = context.getString(R.string.calendar_widget_previous_month_symbol),
            contentDescription = context.getString(R.string.calendar_widget_previous_month),
            monthDelta = -1,
            testTag = CalendarMonthWidgetTestTags.PreviousMonthButton
        )
        Text(
            text = monthLabel,
            modifier = GlanceModifier
                .defaultWeight()
                .semantics { testTag = CalendarMonthWidgetTestTags.MonthLabel },
            style = TextStyle(
                color = ColorProvider(TitleColor),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        MonthNavigationButton(
            text = context.getString(R.string.calendar_widget_next_month_symbol),
            contentDescription = context.getString(R.string.calendar_widget_next_month),
            monthDelta = 1,
            testTag = CalendarMonthWidgetTestTags.NextMonthButton
        )
    }
}

@Composable
private fun MonthNavigationButton(
    text: String,
    contentDescription: String,
    monthDelta: Int,
    testTag: String
) {
    Box(
        modifier = GlanceModifier
            .size(34.dp)
            .semantics {
                this.testTag = testTag
                this.contentDescription = contentDescription
            }
            .clickable(
                actionRunCallback<CalendarMonthWidgetMonthNavigationCallback>(
                    actionParametersOf(CalendarMonthWidgetActionParameters.MonthDelta.to(monthDelta))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(TitleColor),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun WidgetMessage(text: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            modifier = GlanceModifier.semantics { testTag = CalendarMonthWidgetTestTags.ErrorMessage },
            style = TextStyle(
                color = ColorProvider(SecondaryTextColor),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 2
        )
    }
}

@Composable
private fun WeekdayHeader(labels: List<String>) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(SecondaryTextColor),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarMonthWidgetDay,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    val paddedModifier = modifier.padding(horizontal = 1.dp, vertical = 2.dp)
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
    val cellModifier = if (day.isToday) {
        baseModifier.background(ColorProvider(TodayBackground))
    } else {
        baseModifier
    }
    val textColor = when {
        day.isToday -> TodayTextColor
        day.isCurrentMonth -> PrimaryTextColor
        else -> MutedTextColor
    }

    Column(
        modifier = cellModifier.padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.dayLabel,
            modifier = GlanceModifier.semantics {
                testTag = CalendarMonthWidgetTestTags.dayLabel(day.date.toString())
            },
            style = TextStyle(
                color = ColorProvider(textColor),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1
        )
        if (day.taskCountLabel != null && day.isCurrentMonth) {
            Text(
                text = day.taskCountLabel,
                modifier = GlanceModifier.semantics {
                    testTag = CalendarMonthWidgetTestTags.dayTaskCount(day.date.toString())
                },
                style = TextStyle(
                    color = ColorProvider(AccentColor),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        } else {
            Spacer(modifier = GlanceModifier.size(9.dp))
        }
    }
}

private val WidgetBackground = Color(0xFFF8F9FC)
private val TitleColor = Color(0xFF172033)
private val PrimaryTextColor = Color(0xFF263248)
private val SecondaryTextColor = Color(0xFF69758B)
private val MutedTextColor = Color(0xFFADB5C4)
private val TodayBackground = Color(0xFFE7F0FF)
private val TodayTextColor = Color(0xFF1455C0)
private val AccentColor = Color(0xFFDE5B48)

internal object CalendarMonthWidgetTestTags {
    const val Root = "calendar_widget_root"
    const val MonthLabel = "calendar_widget_month_label"
    const val PreviousMonthButton = "calendar_widget_previous_month_button"
    const val NextMonthButton = "calendar_widget_next_month_button"
    const val ErrorMessage = "calendar_widget_error_message"

    fun day(date: String): String = "calendar_widget_day_$date"
    fun dayLabel(date: String): String = "calendar_widget_day_label_$date"
    fun dayTaskCount(date: String): String = "calendar_widget_day_task_count_$date"
}
