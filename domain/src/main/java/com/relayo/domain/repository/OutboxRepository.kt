package com.relayo.domain.repository

import com.relayo.domain.model.PendingMessage
import kotlinx.coroutines.flow.Flow

interface OutboxRepository {
    fun observePendingMessages():Flow<List<PendingMessage>>
    suspend fun enqueue(recipientId:String, payloadType:String, payloadBytes:ByteArray)
    suspend fun markDelivered(id:String)
    suspend fun incrementAttempt(id:String)
}