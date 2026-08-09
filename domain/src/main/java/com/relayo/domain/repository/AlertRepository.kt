package com.relayo.domain.repository

import com.relayo.domain.model.AlertSeverity
import com.relayo.domain.model.EmergencyAlert
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    fun observeAlerts():Flow<List<EmergencyAlert>>
    suspend fun sendAlert(message:String, severity:AlertSeverity)
}