package com.neo.yourtodo.feature.calendar.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

@Composable
internal fun CalendarMonthHeader(
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
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = monthLabel,
                modifier = GlanceModifier.semantics {
                    testTag = CalendarMonthWidgetTestTags.MonthLabel
                },
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
