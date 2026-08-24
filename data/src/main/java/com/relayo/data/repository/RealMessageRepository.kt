package com.relayo.data.repository

import android.content.Context
import android.bluetooth.BluetoothManager
import android.util.Base64
import com.relayo.core.crypto.AesGcmCipher
import com.relayo.core.crypto.EcdhKeyAgreement
import com.relayo.core.crypto.EncryptedPayload
import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.local.MessageDao
import com.relayo.data.local.MessageEntity
import com.relayo.data.wire.DeliveryAckWire
import com.relayo.data.wire.DeliveryAckWireCodec
import com.relayo.data.wire.KeyExchangeWire
import com.relayo.data.wire.KeyExchangeWireCodec
import com.relayo.data.wire.MessageWire
import com.relayo.data.wire.MessageWireCodec
import com.relayo.domain.model.ConversationSummary
import com.relayo.domain.model.EphemeralIdentity
import com.relayo.domain.model.Message
import com.relayo.domain.filter.ContentFilter
import com.relayo.domain.repository.IdentityRepository
import com.relayo.domain.repository.MeshRepository
import com.relayo.domain.repository.MessageRepository
import com.relayo.domain.repository.OutboxRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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

private const val PAYLOAD_TYPE_MESSAGE = "message"
private const val PAYLOAD_TYPE_KEY_EXCHANGE = "key_exchange"
private const val PAYLOAD_TYPE_DELIVERY_ACK = "delivery_ack"

@OptIn(InternalSerializationApi::class)
@Singleton
class RealMessageRepository @Inject constructor(
    private val floodRouter:MeshFloodRouter,
    private val cipher:AesGcmCipher,
    private val ecdh:EcdhKeyAgreement,
    private val meshRepository:MeshRepository,
    private val identityRepository:IdentityRepository,
    private val contentFilter:ContentFilter,
    private val messageDao:MessageDao,
    private val outboxRepository:OutboxRepository,
    @ApplicationContext private val context:Context
):MessageRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val conversationFlows = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private val knownPeerDisplayNames = mutableMapOf<String, String>()

    private val peerPublicKeys = mutableMapOf<String, ByteArray>()
    private val derivedKeys = mutableMapOf<String, SecretKey>()
    private val derivedKeysLock = Any()
    private val bluetoothToSession = mutableMapOf<String, String>()
    private val sessionToBluetooth = mutableMapOf<String, String>()

    @Volatile
    private var currentIdentity:EphemeralIdentity? = null
    @Volatile
    private var currentNearbyIds:Set<String> = emptySet()

    private fun flowFor(peerId:String):MutableStateFlow<List<Message>> =
        conversationFlows.getOrPut(peerId) { MutableStateFlow(emptyList()) }

    init {
        repositoryScope.launch {
            identityRepository.observeIdentity().collect { identity ->
                currentIdentity = identity
                synchronized(derivedKeysLock) { derivedKeys.clear() }
                if(identity != null) {
                    broadcastPublicKey(identity)
                }
            }
        }

        repositoryScope.launch {
            meshRepository.observeNearbyDevices().collect { nearby ->
                currentNearbyIds = nearby.map { it.id }.toSet()
                nearby.forEach { device -> knownPeerDisplayNames[device.id] = device.displayName }
                currentIdentity?.let { broadcastPublicKey(it) }
            }
        }

        repositoryScope.launch(Dispatchers.IO) {
            val persisted = messageDao.getAll()
            persisted.groupBy { it.peerId }.forEach { (peerId, entities) ->
                val messages = entities.map { it.toDomain() }.sortedBy { it.timestampEpochMillis }
                flowFor(peerId).value = messages
            }
        }

        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                when(received.payloadType) {
                    PAYLOAD_TYPE_KEY_EXCHANGE -> handleKeyExchange(received.payloadBytes)
                    PAYLOAD_TYPE_MESSAGE -> handleIncomingMessage(received.payloadBytes)
                    PAYLOAD_TYPE_DELIVERY_ACK -> { /* handled by Outbox, ignore here */ }
                }
            }
        }
    }

    private fun MessageEntity.toDomain() = Message(
        id = id,
        senderId = senderId,
        recipientId = recipientId,
        content = content,
        timestampEpochMillis = timestampEpochMillis,
        isFromMe = isFromMe
    )

    private fun Message.toEntity(peerId:String) = MessageEntity(
        id = id,
        peerId = peerId,
        senderId = senderId,
        recipientId = recipientId,
        content = content,
        timestampEpochMillis = timestampEpochMillis,
        isFromMe = isFromMe
    )

    private suspend fun persistMessage(peerId:String, message:Message) {
        try {
            messageDao.insert(message.toEntity(peerId))
        } catch(_:Exception) {}
    }

    private suspend fun broadcastPublicKey(identity:EphemeralIdentity) {
        try {
            val wire = KeyExchangeWire(
                senderId = mySenderId(identity),
                publicKeyBase64 = Base64.encodeToString(identity.publicKeyBytes, Base64.NO_WRAP),
                timestampEpochMillis = System.currentTimeMillis(),
                sessionId = identity.sessionId
            )
            floodRouter.broadcast(PAYLOAD_TYPE_KEY_EXCHANGE, KeyExchangeWireCodec.encode(wire))
        } catch(e:Exception) {
        }
    }

    private fun mySenderId(identity:EphemeralIdentity? = currentIdentity):String {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.address?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }?.let { return it }
        } catch(e:SecurityException) {
        }
        identity?.let { return it.sessionId }
        return currentIdentity?.sessionId ?: "unknown"
    }

    private fun handleKeyExchange(payloadBytes:ByteArray) {
        val wire = KeyExchangeWireCodec.decode(payloadBytes) ?: return
        if(wire.senderId == mySenderId() && wire.sessionId == currentIdentity?.sessionId) return
        try {
            val pubBytes = Base64.decode(wire.publicKeyBase64, Base64.NO_WRAP)
            if(!ecdh.isValidPublicKey(pubBytes)) return
            val identity = currentIdentity ?: return
            peerPublicKeys[wire.senderId] = pubBytes
            if(wire.sessionId.isNotBlank()) {
                peerPublicKeys[wire.sessionId] = pubBytes
                bluetoothToSession[wire.senderId] = wire.sessionId
                sessionToBluetooth[wire.sessionId] = wire.senderId
                knownPeerDisplayNames[wire.senderId] = knownPeerDisplayNames[wire.senderId] ?: wire.senderId.takeLast(6)
                knownPeerDisplayNames[wire.sessionId] = knownPeerDisplayNames[wire.senderId] ?: wire.sessionId.takeLast(6)
            }
            val sharedKey = ecdh.deriveSharedKey(identity.privateKeyBytes, pubBytes)
            synchronized(derivedKeysLock) {
                derivedKeys[wire.senderId] = sharedKey
                if(wire.sessionId.isNotBlank()) derivedKeys[wire.sessionId] = sharedKey
            }
        } catch(e:Exception) {
        }
    }

    private fun getOrDeriveKey(peerId:String):SecretKey? {
        synchronized(derivedKeysLock) {
            derivedKeys[peerId]?.let { return it }
            bluetoothToSession[peerId]?.let { session -> derivedKeys[session]?.let { return it } }
            sessionToBluetooth[peerId]?.let { bt -> derivedKeys[bt]?.let { return it } }
        }
        val identity = currentIdentity ?: return null
        var peerPub = peerPublicKeys[peerId]
        if(peerPub == null) {
            bluetoothToSession[peerId]?.let { peerPub = peerPublicKeys[it] }
        }
        if(peerPub == null) {
            sessionToBluetooth[peerId]?.let { peerPub = peerPublicKeys[it] }
        }
        if(peerPub == null) return null
        return try {
            val derived = ecdh.deriveSharedKey(identity.privateKeyBytes, peerPub)
            synchronized(derivedKeysLock) {
                derivedKeys[peerId] = derived
                bluetoothToSession[peerId]?.let { derivedKeys[it] = derived }
                sessionToBluetooth[peerId]?.let { derivedKeys[it] = derived }
            }
            derived
        } catch(e:Exception) {
            null
        }
    }

    private fun getOrCreateFallbackKey(peerId:String):SecretKey {
        synchronized(derivedKeysLock) {
            derivedKeys[peerId]?.let { return it }
        }
        val identity = currentIdentity
        val fallbackBytes = try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val input = (peerId + (identity?.sessionId ?: "fallback")).toByteArray(Charsets.UTF_8)
            md.digest(input)
        } catch(e:Exception) {
            ByteArray(32) { 0 }
        }
        val key = cipher.keyFromBytes(fallbackBytes)
        synchronized(derivedKeysLock) { derivedKeys[peerId] = key }
        return key
    }

    private suspend fun sendDeliveryAck(originalMessageId:String, originalSenderId:String) {
        try {
            val ackWire = DeliveryAckWire(
                originalMessageId = originalMessageId,
                ackSenderId = mySenderId(),
                originalSenderId = originalSenderId,
                timestampEpochMillis = System.currentTimeMillis()
            )
            floodRouter.broadcast(PAYLOAD_TYPE_DELIVERY_ACK, DeliveryAckWireCodec.encode(ackWire))
        } catch(_:Exception) {}
    }

    private suspend fun handleIncomingMessage(payloadBytes:ByteArray) {
        val wire = MessageWireCodec.decode(payloadBytes) ?: return
        val myIds = setOfNotNull(mySenderId(), currentIdentity?.sessionId, "me")
        if(wire.recipientId !in myIds && wire.recipientId != "unknown") {
        }
        val peerId = wire.senderId
        val key = getOrDeriveKey(peerId) ?: getOrCreateFallbackKey(peerId)
        try {
            val content = cipher.decrypt(
                key,
                EncryptedPayload(
                    ivBytes = Base64.decode(wire.ivBase64, Base64.NO_WRAP),
                    cipherBytes = Base64.decode(wire.cipherBase64, Base64.NO_WRAP)
                )
            )
            if(!contentFilter.isAllowed(content)) return
            val messageId = wire.messageId.ifBlank { "msg-${System.nanoTime()}" }
            val message = Message(
                id = messageId,
                senderId = wire.senderId,
                recipientId = wire.recipientId,
                content = content,
                timestampEpochMillis = wire.timestampEpochMillis,
                isFromMe = false
            )
            // Deduplicate by messageId
            if(flowFor(peerId).value.any { it.id == messageId }) return
            flowFor(peerId).value = flowFor(peerId).value + message
            persistMessage(peerId, message)
            // Send delivery ack back to sender
            sendDeliveryAck(messageId, wire.senderId)
        } catch(e:Exception) {
        }
    }

    override fun observeConversation(peerId:String) = flowFor(peerId).asStateFlow()

    override fun observeConversations() = combine(
        meshRepository.observeNearbyDevices()
    ) { nearbyArray ->
        val nearby = nearbyArray[0]
        val nearbyIds = nearby.map { it.id }.toSet()

        nearby.forEach { device -> knownPeerDisplayNames[device.id] = device.displayName }

        val allKnownPeerIds = (conversationFlows.keys + nearbyIds + peerPublicKeys.keys).toSet()

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
        if(!contentFilter.isAllowed(content)) return
        val key = getOrDeriveKey(peerId) ?: run {
            currentIdentity?.let { broadcastPublicKey(it) }
            getOrCreateFallbackKey(peerId)
        }
        val encrypted = cipher.encrypt(key, content)
        val messageId = "msg-${System.nanoTime()}-${(0..9999).random()}"
        val wire = MessageWire(
            messageId = messageId,
            senderId = mySenderId(),
            recipientId = peerId,
            ivBase64 = Base64.encodeToString(encrypted.ivBytes, Base64.NO_WRAP),
            cipherBase64 = Base64.encodeToString(encrypted.cipherBytes, Base64.NO_WRAP),
            timestampEpochMillis = System.currentTimeMillis()
        )

        val localMessage = Message(
            id = messageId,
            senderId = mySenderId(),
            recipientId = peerId,
            content = content,
            timestampEpochMillis = wire.timestampEpochMillis,
            isFromMe = true
        )
        flowFor(peerId).value = flowFor(peerId).value + localMessage
        persistMessage(peerId, localMessage)

        val encoded = MessageWireCodec.encode(wire)
        // If peer is not currently nearby, enqueue for store-and-forward
        if(peerId !in currentNearbyIds) {
            try {
                outboxRepository.enqueue(peerId, PAYLOAD_TYPE_MESSAGE, encoded)
            } catch(_:Exception) {
                floodRouter.broadcast(PAYLOAD_TYPE_MESSAGE, encoded)
            }
        } else {
            floodRouter.broadcast(PAYLOAD_TYPE_MESSAGE, encoded)
            // Also enqueue as backup for ack-based confirmation; outbox will remove on ack
            try {
                outboxRepository.enqueue(peerId, PAYLOAD_TYPE_MESSAGE, encoded)
            } catch(_:Exception) {}
        }
    }
}
