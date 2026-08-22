package com.relayo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PendingMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RelayoDatabase:RoomDatabase() {
    abstract fun pendingMessageDao():PendingMessageDao
}