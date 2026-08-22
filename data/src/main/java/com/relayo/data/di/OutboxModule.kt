package com.relayo.data.di

import com.relayo.data.repository.OutboxRepositoryImpl
import com.relayo.domain.repository.OutboxRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OutboxModule {

    @Binds
    abstract fun bindOutboxRepository(
        impl:OutboxRepositoryImpl
    ):OutboxRepository
}