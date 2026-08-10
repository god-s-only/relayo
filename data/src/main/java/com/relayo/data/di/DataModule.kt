package com.relayo.data.di

import com.relayo.data.repository.RealMeshRepository
import com.relayo.domain.repository.MeshRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindMeshRepository(
        impl:RealMeshRepository
    ):MeshRepository
}