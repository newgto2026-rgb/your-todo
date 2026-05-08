package com.neo.yourtodo.core.data.di

import com.neo.yourtodo.core.data.repository.FriendRepositoryImpl
import com.neo.yourtodo.core.domain.repository.FriendRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FriendRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindFriendRepository(impl: FriendRepositoryImpl): FriendRepository
}
