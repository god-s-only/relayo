package com.relayo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PendingMessageEntity::class, MessageEntity::class, NewsPostEntity::class, AlertEntity::class],
    version = 2,
    exportSchema = false
)
abstract class RelayoDatabase:RoomDatabase() {
    abstract fun pendingMessageDao():PendingMessageDao
    abstract fun messageDao():MessageDao
    abstract fun newsPostDao():NewsPostDao
    abstract fun alertDao():AlertDao
}