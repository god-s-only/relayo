package com.relayo.data.di

import com.relayo.data.repository.BridgeRepositoryImpl
import com.relayo.domain.repository.BridgeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BridgeModule {

    @Binds
    abstract fun bindBridgeRepository(
        impl:BridgeRepositoryImpl
    ):BridgeRepository
}
