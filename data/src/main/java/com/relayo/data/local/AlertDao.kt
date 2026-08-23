package com.relayo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts ORDER BY timestampEpochMillis DESC")
    fun observeAll():Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts ORDER BY timestampEpochMillis DESC")
    suspend fun getAll():List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity:AlertEntity)

    @Query("DELETE FROM alerts")
    suspend fun clearAll()
}
