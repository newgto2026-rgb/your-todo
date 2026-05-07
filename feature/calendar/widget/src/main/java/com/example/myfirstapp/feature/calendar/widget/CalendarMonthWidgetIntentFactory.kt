package com.example.myfirstapp.feature.calendar.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.example.myfirstapp.feature.calendar.api.CalendarWidgetIntentContract
import java.time.LocalDate

internal object CalendarMonthWidgetIntentFactory {
    fun openDateIntent(context: Context, date: LocalDate): Intent {
        val baseIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)

        return baseIntent.apply {
            action = CalendarWidgetIntentContract.ACTION_OPEN_CALENDAR_DATE
            putExtra(CalendarWidgetIntentContract.EXTRA_SELECTED_DATE, date.toString())
            data = "myfirstapp://calendar/date/$date".toUri()
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
