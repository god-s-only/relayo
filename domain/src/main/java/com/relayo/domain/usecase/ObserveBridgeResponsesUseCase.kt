package com.relayo.domain.usecase

import com.relayo.domain.repository.BridgeRepository
import javax.inject.Inject

class ObserveBridgeResponsesUseCase @Inject constructor(
    private val repository:BridgeRepository
) {
    operator fun invoke() = repository.observeResponses()
}
