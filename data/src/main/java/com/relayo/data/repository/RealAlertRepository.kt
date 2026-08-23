package com.relayo.data.repository

import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.local.AlertDao
import com.relayo.data.local.AlertEntity
import com.relayo.data.wire.AlertWire
import com.relayo.data.wire.AlertWireCodec
import com.relayo.domain.filter.ContentFilter
import com.relayo.domain.model.AlertSeverity
import com.relayo.domain.model.EmergencyAlert
import com.relayo.domain.repository.AlertRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import javax.inject.Inject
import javax.inject.Singleton

private const val PAYLOAD_TYPE = "alert"

@OptIn(InternalSerializationApi::class)
@Singleton
class RealAlertRepository @Inject constructor(
    private val floodRouter:MeshFloodRouter,
    private val contentFilter:ContentFilter,
    private val alertDao:AlertDao
):AlertRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())

    init {
        repositoryScope.launch(Dispatchers.IO) {
            val persisted = alertDao.getAll().map { it.toDomain() }
            if(persisted.isNotEmpty()) {
                _alerts.value = persisted
            }
        }

        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                if(received.payloadType != PAYLOAD_TYPE) return@collect
                val wire = AlertWireCodec.decode(received.payloadBytes) ?: return@collect
                if(!contentFilter.isAllowed(wire.message)) return@collect
                val severity = try {
                    AlertSeverity.valueOf(wire.severity)
                } catch(e:IllegalArgumentException) {
                    AlertSeverity.WARNING
                }
                val hopCount = (MeshFloodRouter.DEFAULT_TTL - received.remainingTtl).coerceAtLeast(0)
                val alert = EmergencyAlert(
                    id = "alert-${System.nanoTime()}",
                    authorId = wire.authorId,
                    authorDisplayName = wire.authorDisplayName,
                    message = wire.message,
                    severity = severity,
                    timestampEpochMillis = wire.timestampEpochMillis,
                    hopCount = hopCount
                )
                _alerts.value = listOf(alert) + _alerts.value
                persistAlert(alert)
            }
        }
    }

    private fun AlertEntity.toDomain() = EmergencyAlert(
        id = id,
        authorId = authorId,
        authorDisplayName = authorDisplayName,
        message = message,
        severity = try { AlertSeverity.valueOf(severity) } catch(_:Exception) { AlertSeverity.WARNING },
        timestampEpochMillis = timestampEpochMillis,
        hopCount = hopCount
    )

    private fun EmergencyAlert.toEntity() = AlertEntity(
        id = id,
        authorId = authorId,
        authorDisplayName = authorDisplayName,
        message = message,
        severity = severity.name,
        timestampEpochMillis = timestampEpochMillis,
        hopCount = hopCount
    )

    private suspend fun persistAlert(alert:EmergencyAlert) = withContext(Dispatchers.IO) {
        try { alertDao.insert(alert.toEntity()) } catch(_:Exception) {}
    }

    override fun observeAlerts() = _alerts.asStateFlow()

    @OptIn(InternalSerializationApi::class)
    override suspend fun sendAlert(message:String, severity:AlertSeverity) {
        if(!contentFilter.isAllowed(message)) return
        val wire = AlertWire(
            authorId = "me",
            authorDisplayName = "You",
            message = message,
            severity = severity.name,
            timestampEpochMillis = System.currentTimeMillis()
        )
        val local = EmergencyAlert(
            id = "alert-${System.nanoTime()}",
            authorId = "me",
            authorDisplayName = "You",
            message = message,
            severity = severity,
            timestampEpochMillis = wire.timestampEpochMillis,
            hopCount = 0
        )
        _alerts.value = listOf(local) + _alerts.value
        persistAlert(local)

        floodRouter.broadcast(PAYLOAD_TYPE, AlertWireCodec.encode(wire), initialTtl = MeshFloodRouter.DEFAULT_TTL)
    }
}
