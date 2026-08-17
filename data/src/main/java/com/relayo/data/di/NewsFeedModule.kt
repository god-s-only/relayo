package com.relayo.data.di

import com.relayo.data.repository.RealNewsFeedRepository
import com.relayo.domain.repository.NewsFeedRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NewsFeedModule {

    @Binds
    abstract fun bindNewsFeedRepository(
        impl:RealNewsFeedRepository
    ):NewsFeedRepository
}