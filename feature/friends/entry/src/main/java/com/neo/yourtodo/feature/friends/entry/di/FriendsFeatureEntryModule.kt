package com.neo.yourtodo.feature.friends.entry.di

import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import com.neo.yourtodo.feature.friends.impl.navigation.FriendsFeatureEntryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class FriendsFeatureEntryModule {

    @Binds
    @IntoSet
    abstract fun bindFriendsFeatureEntry(entry: FriendsFeatureEntryImpl): AppFeatureEntry
}
