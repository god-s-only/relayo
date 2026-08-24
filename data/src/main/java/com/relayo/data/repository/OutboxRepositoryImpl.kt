package com.relayo.data.repository

import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.local.PendingMessageDao
import com.relayo.data.local.PendingMessageEntity
import com.relayo.data.wire.DeliveryAckWireCodec
import com.relayo.data.wire.MessageWireCodec
import com.relayo.domain.model.PendingMessage
import com.relayo.domain.repository.MeshRepository
import com.relayo.domain.repository.OutboxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ATTEMPTS = 20
private const val PAYLOAD_TYPE_DELIVERY_ACK = "delivery_ack"

@OptIn(InternalSerializationApi::class)
@Singleton
class OutboxRepositoryImpl @Inject constructor(
    private val dao:PendingMessageDao,
    private val floodRouter:MeshFloodRouter,
    private val meshRepository:MeshRepository
):OutboxRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        repositoryScope.launch {
            meshRepository.observeNearbyDevices().collect {
                attemptFlush()
            }
        }
        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                if(received.payloadType == PAYLOAD_TYPE_DELIVERY_ACK) {
                    handleDeliveryAck(received.payloadBytes)
                }
            }
        }
    }

    override fun observePendingMessages():Flow<List<PendingMessage>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun enqueue(recipientId:String, payloadType:String, payloadBytes:ByteArray) {
        // Try to use messageId from payload as the pending id for ack correlation
        val messageId = extractMessageId(payloadType, payloadBytes) ?: "pending-${System.nanoTime()}"
        // Deduplicate: if already pending with same messageId, don't enqueue again
        if(dao.getById(messageId) != null) return
        val entity = PendingMessageEntity(
            id = messageId,
            recipientId = recipientId,
            payloadType = payloadType,
            payloadBytes = payloadBytes,
            queuedAtEpochMillis = System.currentTimeMillis(),
            attemptCount = 0
        )
        dao.insert(entity)
        attemptFlush()
    }

    private fun extractMessageId(payloadType:String, payloadBytes:ByteArray):String? {
        if(payloadType != "message") return null
        return try {
            MessageWireCodec.decode(payloadBytes)?.messageId?.takeIf { it.isNotBlank() }
        } catch(_:Exception) { null }
    }

    override suspend fun markDelivered(id:String) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    override suspend fun incrementAttempt(id:String) {
        dao.getById(id)?.let { entity ->
            dao.update(entity.copy(attemptCount = entity.attemptCount + 1))
        }
    }

    private suspend fun handleDeliveryAck(payloadBytes:ByteArray) {
        val ack = DeliveryAckWireCodec.decode(payloadBytes) ?: return
        // Ack contains originalMessageId — if we have a pending with that id, it's delivered
        val pending = dao.getById(ack.originalMessageId) ?: return
        dao.delete(pending)
    }

    private suspend fun attemptFlush() {
        val pending = dao.getAll()
        pending.forEach { entity ->
            if(entity.attemptCount >= MAX_ATTEMPTS) return@forEach
            floodRouter.broadcast(entity.payloadType, entity.payloadBytes)
            incrementAttempt(entity.id)
        }
    }

    private fun PendingMessageEntity.toDomain() = PendingMessage(
        id = id,
        recipientId = recipientId,
        payloadType = payloadType,
        payloadBytes = payloadBytes,
        queuedAtEpochMillis = queuedAtEpochMillis,
        attemptCount = attemptCount
    )
}
