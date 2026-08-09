package com.relayo.domain.usecase

import com.relayo.domain.model.EmergencyAlert
import com.relayo.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAlertsUseCase @Inject constructor(
    private val repository:AlertRepository
) {
    operator fun invoke():Flow<List<EmergencyAlert>> = repository.observeAlerts()
}