package com.relayo.domain.usecase

import com.relayo.domain.repository.MessageRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository:MessageRepository
) {
    suspend operator fun invoke(peerId:String, content:String) {
        repository.sendMessage(peerId, content)
    }
}