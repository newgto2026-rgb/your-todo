package com.neo.yourtodo.feature.auth.entry.di

import com.neo.yourtodo.feature.auth.api.AuthGateEntry
import com.neo.yourtodo.feature.auth.impl.AuthGateEntryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthGateEntryModule {

    @Binds
    abstract fun bindAuthGateEntry(entry: AuthGateEntryImpl): AuthGateEntry
}
