package com.neo.yourtodo.feature.calendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.layout.width
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
