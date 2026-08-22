package com.relayo.domain.usecase

import com.relayo.domain.model.ConversationSummary
import com.relayo.domain.repository.VoiceNoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveVoiceNoteConversationsUseCase @Inject constructor(
    private val repository:VoiceNoteRepository
) {
    operator fun invoke():Flow<List<ConversationSummary>> = repository.observeVoiceNoteConversations()
}