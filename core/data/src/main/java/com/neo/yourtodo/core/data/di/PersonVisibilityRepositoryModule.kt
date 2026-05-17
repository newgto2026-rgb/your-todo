package com.neo.yourtodo.core.data.di

import com.neo.yourtodo.core.data.repository.PersonVisibilityRepositoryImpl
import com.neo.yourtodo.core.domain.repository.PersonVisibilityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PersonVisibilityRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPersonVisibilityRepository(
        impl: PersonVisibilityRepositoryImpl
    ): PersonVisibilityRepository
}
