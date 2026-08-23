package com.relayo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id:String,
    val peerId:String,
    val senderId:String,
    val recipientId:String,
    val content:String,
    val timestampEpochMillis:Long,
    val isFromMe:Boolean
)
