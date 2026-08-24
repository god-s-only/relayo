package com.relayo.core.mesh

import com.relayo.core.transport.MeshMessenger
import com.relayo.core.transport.PeerScanner
import com.relayo.domain.filter.ContentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class ReceivedPayload(
    val payloadType:String,
    val payloadBytes:ByteArray,
    val originAddress:String,
    val remainingTtl:Int
)

@Singleton
class MeshFloodRouter @Inject constructor(
    private val messenger:MeshMessenger,
    private val peerScanner:PeerScanner,
    private val contentFilter:ContentFilter
) {

    private val routerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val random = SecureRandom()

    private val seenMessageIds = object:LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String, Long>?):Boolean {
            return size > 200
        }
    }

    private val envelopeStore = object:LinkedHashMap<String, MeshEnvelope>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String, MeshEnvelope>?):Boolean {
            return size > 200
        }
    }

    private val knownPeerAddresses = mutableSetOf<String>()

    private val _incomingPayloads = MutableSharedFlow<ReceivedPayload>(extraBufferCapacity = 32)
    val incomingPayloads = _incomingPayloads.asSharedFlow()

    private var started = false

    fun start() {
        if(started) return
        started = true

        routerScope.launch {
            peerScanner.scan().collect { peer ->
                knownPeerAddresses.add(peer.address)
            }
        }

        routerScope.launch {
            messenger.observeIncoming().collect { incoming ->
                val envelope = EnvelopeCodec.decode(incoming.payload) ?: return@collect
                handleEnvelope(envelope)
            }
        }
    }

    suspend fun broadcast(payloadType:String, payloadBytes:ByteArray, initialTtl:Int = DEFAULT_TTL) {
        val envelope = MeshEnvelope(
            messageId = generateMessageId(),
            originAddress = "self",
            ttl = initialTtl,
            payloadType = payloadType,
            payloadBytes = payloadBytes
        )
        markSeen(envelope.messageId)
        storeEnvelope(envelope)
        relayToAllPeers(envelope)
    }

    private suspend fun handleEnvelope(envelope:MeshEnvelope) {
        if(hasSeen(envelope.messageId)) return
        markSeen(envelope.messageId)
        storeEnvelope(envelope)

        // Core mesh-level inbound filter: drop harmful content before it reaches
        // any feature repository. Exempt key exchange and system types.
        if(envelope.payloadType in setOf("message", "news_post", "alert", "board_post", "board_create")) {
            try {
                val payloadText = String(envelope.payloadBytes, Charsets.UTF_8)
                if(!contentFilter.isAllowed(payloadText)) {
                    return
                }
            } catch(e:Exception) {
            }
        }

        _incomingPayloads.tryEmit(
            ReceivedPayload(
                payloadType = envelope.payloadType,
                payloadBytes = envelope.payloadBytes,
                originAddress = envelope.originAddress,
                remainingTtl = envelope.ttl
            )
        )

        if(envelope.ttl > 1) {
            relayToAllPeers(envelope.copy(ttl = envelope.ttl - 1))
        }
    }

    private suspend fun relayToAllPeers(envelope:MeshEnvelope) {
        val bytes = EnvelopeCodec.encode(envelope)
        knownPeerAddresses.forEach { address ->
            messenger.sendTo(address, bytes)
        }
    }

    @Synchronized
    private fun hasSeen(messageId:String):Boolean = seenMessageIds.containsKey(messageId)

    @Synchronized
    private fun markSeen(messageId:String) {
        seenMessageIds[messageId] = System.currentTimeMillis()
    }

    @Synchronized
    private fun storeEnvelope(envelope:MeshEnvelope) {
        envelopeStore[envelope.messageId] = envelope
    }

    @Synchronized
    fun getSeenIdsSnapshot():List<String> = seenMessageIds.keys.toList()

    @Synchronized
    fun getEnvelope(messageId:String):MeshEnvelope? = envelopeStore[messageId]

    private fun generateMessageId():String {
        val bytes = ByteArray(12)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_TTL = 4
    }
}