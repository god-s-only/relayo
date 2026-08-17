package com.relayo.data.di

import com.relayo.data.repository.RealMessageRepository
import com.relayo.domain.repository.MessageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MessageModule {

    @Binds
    abstract fun bindMessageRepository(
        impl:RealMessageRepository
    ):MessageRepository
}