package com.relayo.data.repository

import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.local.PendingMessageDao
import com.relayo.data.local.PendingMessageEntity
import com.relayo.domain.model.PendingMessage
import com.relayo.domain.repository.MeshRepository
import com.relayo.domain.repository.OutboxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ATTEMPTS = 20

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
    }

    override fun observePendingMessages():Flow<List<PendingMessage>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun enqueue(recipientId:String, payloadType:String, payloadBytes:ByteArray) {
        val entity = PendingMessageEntity(
            id = "pending-${System.nanoTime()}",
            recipientId = recipientId,
            payloadType = payloadType,
            payloadBytes = payloadBytes,
            queuedAtEpochMillis = System.currentTimeMillis(),
            attemptCount = 0
        )
        dao.insert(entity)
        attemptFlush()
    }

    override suspend fun markDelivered(id:String) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    override suspend fun incrementAttempt(id:String) {
        dao.getById(id)?.let { entity ->
            dao.update(entity.copy(attemptCount = entity.attemptCount + 1))
        }
    }

    private suspend fun attemptFlush() {
        val pending = dao.getAll()
        pending.forEach { entity ->
            if(entity.attemptCount >= MAX_ATTEMPTS) return@forEach
            val delivered = floodRouter.broadcast(entity.payloadType, entity.payloadBytes)
            // broadcast() currently returns Unit, not a delivery confirmation —
            // see honest note below on what "delivered" actually means here.
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