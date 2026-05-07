package com.neo.yourtodo.feature.calendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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

class CalendarMonthWidget internal constructor(
    private val forcedLayout: CalendarMonthWidgetLayout? = null
) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(CalendarMonthWidgetSizes.All)
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val presenter = runCatching {
            EntryPointAccessors
                .fromApplication(context, CalendarMonthWidgetEntryPoint::class.java)
                .presenter()
        }.getOrNull()

        provideContent {
            if (presenter == null) {
                CalendarMonthWidgetContent(
                    state = calendarMonthWidgetErrorState(),
                    forcedLayout = forcedLayout
                )
            } else {
                CalendarMonthWidgetContent(
                    presenter = presenter,
                    forcedLayout = forcedLayout
                )
            }
        }
    }
}

@Composable
internal fun CalendarMonthWidgetContent(
    presenter: CalendarMonthWidgetPresenter,
    forcedLayout: CalendarMonthWidgetLayout? = null
) {
    val preferences = currentState<Preferences>()
    val state = runCatching {
        runBlocking {
            presenter.present(displayedMonth = preferences.displayedMonthOrNull())
        }
    }.getOrElse {
        calendarMonthWidgetErrorState()
    }

    CalendarMonthWidgetContent(state = state, forcedLayout = forcedLayout)
}

@Composable
internal fun CalendarMonthWidgetContent(
    state: CalendarMonthWidgetState,
    forcedLayout: CalendarMonthWidgetLayout? = null
) {
    val context = LocalContext.current
    val layout = forcedLayout ?: CalendarMonthWidgetLayout.fromSize(LocalSize.current)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBackground))
            .cornerRadius(WidgetCornerRadius)
            .padding(layout.contentPadding)
            .semantics { testTag = CalendarMonthWidgetTestTags.Root }
    ) {
        if (layout == CalendarMonthWidgetLayout.Expanded) {
            Box(
                modifier = GlanceModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                WidgetBrandLogo(layout = layout)
            }
            Spacer(modifier = GlanceModifier.height(layout.brandLogoSpacing))
        }

        CalendarMonthHeader(
            monthLabel = state.monthLabel.ifBlank {
                context.getString(R.string.calendar_widget_name)
            },
            layout = layout
        )

        Spacer(modifier = GlanceModifier.height(layout.headerSpacing))

        if (state.isError) {
            WidgetMessage(text = context.getString(R.string.calendar_widget_error))
            return@Column
        }

        WeekdayHeader(labels = state.weekdayLabels, layout = layout)
        Spacer(modifier = GlanceModifier.height(layout.weekdaySpacing))

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
        ) {
            state.weeks.forEach { week ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                ) {
                    week.forEach { day ->
                        CalendarDayCell(
                            day = day,
                            layout = layout,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
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
private fun WidgetBrandLogo(layout: CalendarMonthWidgetLayout) {
    val context = LocalContext.current

    Image(
        provider = ImageProvider(R.drawable.todo_wordmark),
        contentDescription = context.getString(R.string.calendar_widget_brand_logo),
        modifier = GlanceModifier
            .width(layout.brandLogoWidth)
            .height(layout.brandLogoHeight)
            .semantics { testTag = CalendarMonthWidgetTestTags.BrandLogo }
    )
}

@Composable
private fun CalendarMonthHeader(
    monthLabel: String,
    layout: CalendarMonthWidgetLayout
) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthNavigationButton(
            text = context.getString(R.string.calendar_widget_previous_month_symbol),
            contentDescription = context.getString(R.string.calendar_widget_previous_month),
            monthDelta = -1,
            testTag = CalendarMonthWidgetTestTags.PreviousMonthButton,
            layout = layout
        )
        Row(
            modifier = GlanceModifier
                .defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = monthLabel,
                modifier = GlanceModifier
                    .semantics { testTag = CalendarMonthWidgetTestTags.MonthLabel },
                style = TextStyle(
                    color = ColorProvider(TitleColor),
                    fontSize = layout.titleFontSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
        }
        MonthNavigationButton(
            text = context.getString(R.string.calendar_widget_next_month_symbol),
            contentDescription = context.getString(R.string.calendar_widget_next_month),
            monthDelta = 1,
            testTag = CalendarMonthWidgetTestTags.NextMonthButton,
            layout = layout
        )
    }
}

@Composable
private fun MonthNavigationButton(
    text: String,
    contentDescription: String,
    monthDelta: Int,
    testTag: String,
    layout: CalendarMonthWidgetLayout
) {
    Box(
        modifier = GlanceModifier
            .size(layout.navigationButtonSize)
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
                fontSize = layout.navigationFontSize.sp,
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
private fun WeekdayHeader(
    labels: List<String>,
    layout: CalendarMonthWidgetLayout
) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(SecondaryTextColor),
                    fontSize = layout.weekdayFontSize.sp,
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
        .background(
            ColorProvider(
                when {
                    day.isToday -> TodayBackground
                    day.isCurrentMonth -> DayCellBackground
                    else -> AdjacentDayCellBackground
                }
            )
        )
        .cornerRadius(DayCellCornerRadius)
    val textColor = when {
        day.isToday -> TodayTextColor
        day.isCurrentMonth -> PrimaryTextColor
        else -> MutedTextColor
    }

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
    val backgroundColor = when {
        isToday -> TodayChipBackground
        chip.isOverflow -> OverflowChipBackground
        chip.isDone -> DoneChipBackground
        else -> TodoChipBackground
    }
    val textColor = when {
        isToday -> TodayChipText
        chip.isOverflow -> OverflowChipText
        chip.isDone -> DoneChipText
        else -> TodoChipText
    }

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

private val WidgetBackground = Color(0xFFFAFAFA)
private val TitleColor = Color(0xFF172033)
private val PrimaryTextColor = Color(0xFF263248)
private val SecondaryTextColor = Color(0xFF69758B)
private val MutedTextColor = Color(0xFFADB5C4)
private val DayCellBackground = Color(0xFFFFFFFF)
private val AdjacentDayCellBackground = Color(0xFFFFFFFF)
private val TodayBackground = Color(0xFF1A73E8)
private val TodayTextColor = Color(0xFFFFFFFF)
private val CountTextColor = Color(0xFF174EA6)
private val TodoChipBackground = Color(0xFFE8F0FE)
private val TodoChipText = Color(0xFF174EA6)
private val TodayChipBackground = Color(0xE6FFFFFF)
private val TodayChipText = Color(0xFF174EA6)
private val DoneChipBackground = Color(0xFFC9CEDA)
private val DoneChipText = Color(0xFF586175)
private val OverflowChipBackground = Color(0xFFE2E7F0)
private val OverflowChipText = Color(0xFF536074)
private val WidgetCornerRadius = 28.dp
private val DayCellCornerRadius = 8.dp
private val ChipCornerRadius = 0.dp
private const val TodoChipMaxLines = 2

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
