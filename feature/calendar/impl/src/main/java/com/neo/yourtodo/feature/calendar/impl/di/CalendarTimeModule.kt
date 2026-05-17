package com.neo.yourtodo.feature.calendar.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CalendarZoneId

@Module
@InstallIn(SingletonComponent::class)
object CalendarTimeModule {
    @Provides
    @Singleton
    @CalendarZoneId
    fun provideCalendarZoneId(): ZoneId = ZoneId.systemDefault()
}
