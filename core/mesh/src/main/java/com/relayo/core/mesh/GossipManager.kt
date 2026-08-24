package com.relayo.core.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val GOSSIP_INTERVAL_MS = 20_000L
private const val GOSSIP_DIGEST_SIZE = 20
private const val PAYLOAD_TYPE_GOSSIP_DIGEST = "gossip_digest"
private const val PAYLOAD_TYPE_GOSSIP_REQUEST = "gossip_request"

@Singleton
class GossipManager @Inject constructor(
    private val floodRouter:MeshFloodRouter
) {
    private val gossipScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        if(started) return
        started = true

        // Periodic digest broadcast
        gossipScope.launch {
            while(true) {
                delay(GOSSIP_INTERVAL_MS)
                try {
                    broadcastDigest()
                } catch(_:Exception) {}
            }
        }

        // Handle incoming gossip messages
        gossipScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                when(received.payloadType) {
                    PAYLOAD_TYPE_GOSSIP_DIGEST -> handleDigest(received.payloadBytes)
                    PAYLOAD_TYPE_GOSSIP_REQUEST -> handleRequest(received.payloadBytes)
                }
            }
        }
    }

    private suspend fun broadcastDigest() {
        val seenIds = floodRouter.getSeenIdsSnapshot()
        if(seenIds.isEmpty()) return
        // Take up to GOSSIP_DIGEST_SIZE most recent (LinkedHashMap is access-order, so snapshot is recent)
        val digestIds = seenIds.takeLast(GOSSIP_DIGEST_SIZE)
        val wire = GossipDigestWire(
            senderId = "self",
            messageIds = digestIds,
            timestampEpochMillis = System.currentTimeMillis()
        )
        floodRouter.broadcast(PAYLOAD_TYPE_GOSSIP_DIGEST, GossipWireCodec.encodeDigest(wire))
    }

    private suspend fun handleDigest(payloadBytes:ByteArray) {
        val wire = GossipWireCodec.decodeDigest(payloadBytes) ?: return
        if(wire.senderId == "self") return
        val localSeen = floodRouter.getSeenIdsSnapshot().toSet()
        val missing = wire.messageIds.filter { it !in localSeen }
        if(missing.isEmpty()) return
        // Request one missing at random to avoid storm
        val toRequest = missing.random()
        val requestWire = GossipRequestWire(
            requesterId = "self",
            requestedMessageId = toRequest,
            timestampEpochMillis = System.currentTimeMillis()
        )
        floodRouter.broadcast(PAYLOAD_TYPE_GOSSIP_REQUEST, GossipWireCodec.encodeRequest(requestWire))
    }

    private suspend fun handleRequest(payloadBytes:ByteArray) {
        val wire = GossipWireCodec.decodeRequest(payloadBytes) ?: return
        if(wire.requesterId == "self") return
        val envelope = floodRouter.getEnvelope(wire.requestedMessageId) ?: return
        // Rebroadcast the original envelope's payload as a new gossip-forwarded message
        // Use a new envelope but same payload to trigger normal flood handling on requester
        floodRouter.broadcast(envelope.payloadType, envelope.payloadBytes, initialTtl = MeshFloodRouter.DEFAULT_TTL)
    }
}
