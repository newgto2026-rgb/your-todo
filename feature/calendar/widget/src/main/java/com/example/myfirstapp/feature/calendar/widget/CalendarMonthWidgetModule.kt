package com.example.myfirstapp.feature.calendar.widget

import com.example.myfirstapp.core.domain.scheduler.CalendarWidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CalendarMonthWidgetBindingModule {
    @Binds
    abstract fun bindCalendarMonthSummarySource(
        impl: DomainCalendarMonthSummarySource
    ): CalendarMonthSummarySource

    @Binds
    abstract fun bindCalendarWidgetUpdater(
        impl: GlanceCalendarWidgetUpdater
    ): CalendarWidgetUpdater
}

@Module
@InstallIn(SingletonComponent::class)
internal object CalendarMonthWidgetProviderModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
