package com.relayo.data.di

import com.relayo.data.repository.VoiceNoteRepositoryImpl
import com.relayo.domain.repository.VoiceNoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceNoteModule {

    @Binds
    abstract fun bindVoiceNoteRepository(
        impl:VoiceNoteRepositoryImpl
    ):VoiceNoteRepository
}