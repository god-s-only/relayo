package com.relayo.domain.usecase

import com.relayo.domain.model.ConversationSummary
import com.relayo.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConversationsUseCase @Inject constructor(
    private val repository:MessageRepository
) {
    operator fun invoke():Flow<List<ConversationSummary>> = repository.observeConversations()
}