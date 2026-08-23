package com.relayo.domain.usecase

import com.relayo.domain.model.BridgeRequestType
import com.relayo.domain.repository.BridgeRepository
import javax.inject.Inject

class SendBridgeRequestUseCase @Inject constructor(
    private val repository:BridgeRepository
) {
    suspend operator fun invoke(type:BridgeRequestType, query:String):String =
        repository.sendRequest(type, query)
}
