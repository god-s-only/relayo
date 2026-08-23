package com.relayo.domain.usecase

import com.relayo.domain.repository.BridgeRepository
import javax.inject.Inject

class ObserveBridgeRequestsUseCase @Inject constructor(
    private val repository:BridgeRepository
) {
    operator fun invoke() = repository.observeMyRequests()
}
