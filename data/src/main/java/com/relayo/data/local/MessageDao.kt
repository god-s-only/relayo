package com.relayo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY timestampEpochMillis ASC")
    fun observeForPeer(peerId:String):Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestampEpochMillis ASC")
    fun observeAll():Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestampEpochMillis ASC")
    suspend fun getAll():List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity:MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities:List<MessageEntity>)

    @Query("DELETE FROM messages WHERE peerId = :peerId")
    suspend fun clearForPeer(peerId:String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
