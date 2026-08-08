package com.relayo.data.di

import com.relayo.data.repository.EphemeralIdentityRepository
import com.relayo.domain.repository.IdentityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class IdentityModule {

    @Binds
    abstract fun bindIdentityRepository(
        impl:EphemeralIdentityRepository
    ):IdentityRepository
}