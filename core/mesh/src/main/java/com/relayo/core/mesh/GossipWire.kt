package com.relayo.core.mesh

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class GossipDigestWire(
    val senderId:String,
    val messageIds:List<String>,
    val timestampEpochMillis:Long
)

@Serializable
data class GossipRequestWire(
    val requesterId:String,
    val requestedMessageId:String,
    val timestampEpochMillis:Long
)

object GossipWireCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encodeDigest(wire:GossipDigestWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)
    fun decodeDigest(bytes:ByteArray):GossipDigestWire? = try {
        json.decodeFromString<GossipDigestWire>(String(bytes, Charsets.UTF_8))
    } catch(_:Exception) { null }
    fun encodeRequest(wire:GossipRequestWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)
    fun decodeRequest(bytes:ByteArray):GossipRequestWire? = try {
        json.decodeFromString<GossipRequestWire>(String(bytes, Charsets.UTF_8))
    } catch(_:Exception) { null }
}
