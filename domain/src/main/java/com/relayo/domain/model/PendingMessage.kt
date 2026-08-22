package com.relayo.domain.model

data class PendingMessage(
    val id:String,
    val recipientId:String,
    val payloadType:String,
    val payloadBytes:ByteArray,
    val queuedAtEpochMillis:Long,
    val attemptCount:Int
)