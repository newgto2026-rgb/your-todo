package com.neo.yourtodo.feature.calendar.entry.di

import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import com.neo.yourtodo.feature.calendar.impl.navigation.CalendarFeatureEntryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarFeatureEntryModule {

    @Binds
    @IntoSet
    abstract fun bindCalendarFeatureEntry(entry: CalendarFeatureEntryImpl): AppFeatureEntry
}
