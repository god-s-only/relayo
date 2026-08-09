package com.relayo.domain.usecase

import com.relayo.domain.model.AlertSeverity
import com.relayo.domain.repository.AlertRepository
import javax.inject.Inject

class SendAlertUseCase @Inject constructor(
    private val repository:AlertRepository
) {
    suspend operator fun invoke(message:String, severity:AlertSeverity) {
        repository.sendAlert(message, severity)
    }
}