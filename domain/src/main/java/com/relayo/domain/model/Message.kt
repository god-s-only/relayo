package com.relayo.domain.model

data class Message(
    val id:String,
    val senderId:String,
    val recipientId:String,
    val content:String,
    val timestampEpochMillis:Long,
    val isFromMe:Boolean
)