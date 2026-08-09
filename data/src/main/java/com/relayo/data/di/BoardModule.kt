package com.relayo.data.di

import com.relayo.data.repository.FakeBoardRepository
import com.relayo.domain.repository.BoardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BoardModule {

    @Binds
    abstract fun bindBoardRepository(
        impl:FakeBoardRepository
    ):BoardRepository
}