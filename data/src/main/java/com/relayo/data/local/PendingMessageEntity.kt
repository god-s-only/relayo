package com.relayo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey val id:String,
    val recipientId:String,
    val payloadType:String,
    val payloadBytes:ByteArray,
    val queuedAtEpochMillis:Long,
    val attemptCount:Int
)