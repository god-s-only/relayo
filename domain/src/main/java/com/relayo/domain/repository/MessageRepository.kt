package com.relayo.domain.repository

import com.relayo.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeConversation(peerId:String):Flow<List<Message>>
    suspend fun sendMessage(peerId:String, content:String)
}