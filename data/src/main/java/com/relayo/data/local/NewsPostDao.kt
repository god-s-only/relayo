package com.relayo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsPostDao {

    @Query("SELECT * FROM news_posts ORDER BY timestampEpochMillis DESC")
    fun observeAll():Flow<List<NewsPostEntity>>

    @Query("SELECT * FROM news_posts ORDER BY timestampEpochMillis DESC")
    suspend fun getAll():List<NewsPostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity:NewsPostEntity)

    @Query("DELETE FROM news_posts")
    suspend fun clearAll()
}
