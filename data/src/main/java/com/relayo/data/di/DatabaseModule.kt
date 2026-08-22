package com.relayo.data.di

import android.content.Context
import androidx.room.Room
import com.relayo.data.local.RelayoDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context:Context):RelayoDatabase =
        Room.databaseBuilder(context, RelayoDatabase::class.java, "relayo.db").build()

    @Provides
    fun providePendingMessageDao(database:RelayoDatabase) = database.pendingMessageDao()
}