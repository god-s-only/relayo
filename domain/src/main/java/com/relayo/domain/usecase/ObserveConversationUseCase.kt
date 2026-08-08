package com.relayo.domain.usecase

import com.relayo.domain.model.Message
import com.relayo.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConversationUseCase @Inject constructor(
    private val repository:MessageRepository
) {
    operator fun invoke(peerId:String):Flow<List<Message>> = repository.observeConversation(peerId)
}