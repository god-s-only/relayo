package com.relayo.domain.usecase

import com.relayo.domain.model.PendingMessage
import com.relayo.domain.repository.OutboxRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePendingMessagesUseCase @Inject constructor(
    private val repository:OutboxRepository
) {
    operator fun invoke():Flow<List<PendingMessage>> = repository.observePendingMessages()
}