package com.relayo.data.repository

import com.relayo.domain.model.AlertSeverity
import com.relayo.domain.model.EmergencyAlert
import com.relayo.domain.repository.AlertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAlertRepository @Inject constructor():AlertRepository {

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    private val alertsFlow:StateFlow<List<EmergencyAlert>> = _alerts.asStateFlow()

    override fun observeAlerts() = alertsFlow

    override suspend fun sendAlert(message:String, severity:AlertSeverity) {
        val alert = EmergencyAlert(
            id = "alert-${System.nanoTime()}",
            authorId = "me",
            authorDisplayName = "You",
            message = message,
            severity = severity,
            timestampEpochMillis = System.currentTimeMillis(),
            hopCount = 0
        )
        _alerts.value = listOf(alert) + _alerts.value
    }
}