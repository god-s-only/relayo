package com.relayo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMessageDao {

    @Query("SELECT * FROM pending_messages ORDER BY queuedAtEpochMillis ASC")
    fun observeAll():Flow<List<PendingMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity:PendingMessageEntity)

    @Update
    suspend fun update(entity:PendingMessageEntity)

    @Delete
    suspend fun delete(entity:PendingMessageEntity)

    @Query("SELECT * FROM pending_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id:String):PendingMessageEntity?
}