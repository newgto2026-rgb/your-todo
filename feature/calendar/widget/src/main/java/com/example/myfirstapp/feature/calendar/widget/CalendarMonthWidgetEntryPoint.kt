package com.example.myfirstapp.feature.calendar.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CalendarMonthWidgetEntryPoint {
    fun clock(): Clock

    fun presenter(): CalendarMonthWidgetPresenter
}
