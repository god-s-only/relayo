package com.relayo.data.repository

import android.util.Base64
import com.relayo.core.crypto.AesGcmCipher
import com.relayo.core.crypto.EncryptedPayload
import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.wire.MessageWire
import com.relayo.data.wire.MessageWireCodec
import com.relayo.domain.model.ConversationSummary
import com.relayo.domain.model.Message
import com.relayo.domain.repository.MeshRepository
import com.relayo.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

private const val PAYLOAD_TYPE = "message"

@OptIn(InternalSerializationApi::class)
@Singleton
class RealMessageRepository @Inject constructor(
    private val floodRouter:MeshFloodRouter,
    private val cipher:AesGcmCipher,
    private val meshRepository:MeshRepository
):MessageRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val conversationFlows = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private val knownPeerDisplayNames = mutableMapOf<String, String>()

    private val sessionKey:SecretKey = cipher.generateKey()

    private fun flowFor(peerId:String):MutableStateFlow<List<Message>> =
        conversationFlows.getOrPut(peerId) { MutableStateFlow(emptyList()) }

    init {
        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                if(received.payloadType != PAYLOAD_TYPE) return@collect
                val wire = MessageWireCodec.decode(received.payloadBytes) ?: return@collect
                try {
                    val content = cipher.decrypt(
                        sessionKey,
                        EncryptedPayload(
                            ivBytes = Base64.decode(wire.ivBase64, Base64.NO_WRAP),
                            cipherBytes = Base64.decode(wire.cipherBase64, Base64.NO_WRAP)
                        )
                    )
                    val message = Message(
                        id = "msg-${System.nanoTime()}",
                        senderId = wire.senderId,
                        recipientId = wire.recipientId,
                        content = content,
                        timestampEpochMillis = wire.timestampEpochMillis,
                        isFromMe = false
                    )
                    val peerId = wire.senderId
                    flowFor(peerId).value = flowFor(peerId).value + message
                } catch(e:Exception) {
                }
            }
        }
    }

    override fun observeConversation(peerId:String) = flowFor(peerId).asStateFlow()

    override fun observeConversations() = combine(
        meshRepository.observeNearbyDevices()
    ) { nearbyArray ->
        val nearby = nearbyArray[0]
        val nearbyIds = nearby.map { it.id }.toSet()

        nearby.forEach { device -> knownPeerDisplayNames[device.id] = device.displayName }

        val allKnownPeerIds = (conversationFlows.keys + nearbyIds).toSet()

        allKnownPeerIds.map { peerId ->
            val messages = conversationFlows[peerId]?.value.orEmpty()
            val lastMessage = messages.lastOrNull()
            ConversationSummary(
                peerId = peerId,
                displayName = knownPeerDisplayNames[peerId] ?: peerId.takeLast(6),
                lastMessagePreview = lastMessage?.content,
                lastMessageTimestampEpochMillis = lastMessage?.timestampEpochMillis,
                isOnline = peerId in nearbyIds
            )
        }.sortedByDescending { it.lastMessageTimestampEpochMillis ?: 0L }
    }

    override suspend fun sendMessage(peerId:String, content:String) {
        val encrypted = cipher.encrypt(sessionKey, content)
        val wire = MessageWire(
            senderId = "me",
            recipientId = peerId,
            ivBase64 = Base64.encodeToString(encrypted.ivBytes, Base64.NO_WRAP),
            cipherBase64 = Base64.encodeToString(encrypted.cipherBytes, Base64.NO_WRAP),
            timestampEpochMillis = System.currentTimeMillis()
        )

        val localMessage = Message(
            id = "msg-${System.nanoTime()}",
            senderId = "me",
            recipientId = peerId,
            content = content,
            timestampEpochMillis = wire.timestampEpochMillis,
            isFromMe = true
        )
        flowFor(peerId).value = flowFor(peerId).value + localMessage

        floodRouter.broadcast(PAYLOAD_TYPE, MessageWireCodec.encode(wire))
    }
}