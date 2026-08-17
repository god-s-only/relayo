package com.relayo.data.repository

import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.wire.AlertWire
import com.relayo.data.wire.AlertWireCodec
import com.relayo.domain.model.AlertSeverity
import com.relayo.domain.model.EmergencyAlert
import com.relayo.domain.repository.AlertRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import javax.inject.Inject
import javax.inject.Singleton

private const val PAYLOAD_TYPE = "alert"

@OptIn(InternalSerializationApi::class)
@Singleton
class RealAlertRepository @Inject constructor(
    private val floodRouter:MeshFloodRouter
):AlertRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())

    init {
        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                if(received.payloadType != PAYLOAD_TYPE) return@collect
                val wire = AlertWireCodec.decode(received.payloadBytes) ?: return@collect
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
            }
        }
    }

    override fun observeAlerts() = _alerts.asStateFlow()

    @OptIn(InternalSerializationApi::class)
    override suspend fun sendAlert(message:String, severity:AlertSeverity) {
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

        floodRouter.broadcast(PAYLOAD_TYPE, AlertWireCodec.encode(wire), initialTtl = MeshFloodRouter.DEFAULT_TTL)
    }
}