package com.relayo.data.di

import com.relayo.data.filter.DefaultContentFilter
import com.relayo.domain.filter.ContentFilter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class FilterModule {

    @Binds
    abstract fun bindContentFilter(
        impl:DefaultContentFilter
    ):ContentFilter
}
