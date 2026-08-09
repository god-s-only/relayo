package com.relayo.data.di

import com.relayo.data.repository.FakeAlertRepository
import com.relayo.domain.repository.AlertRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertModule {

    @Binds
    abstract fun bindAlertRepository(
        impl:FakeAlertRepository
    ):AlertRepository
}