package com.relayo.data.wire

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
@InternalSerializationApi
data class BridgeRequestWire(
    val id:String,
    val requesterId:String,
    val type:String,
    val query:String,
    val timestampEpochMillis:Long
)

@Serializable
@InternalSerializationApi
data class BridgeResponseWire(
    val requestId:String,
    val responderId:String,
    val result:String,
    val error:String? = null,
    val timestampEpochMillis:Long
)

object BridgeWireCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(InternalSerializationApi::class)
    fun encodeRequest(wire:BridgeRequestWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)

    @OptIn(InternalSerializationApi::class)
    fun decodeRequest(bytes:ByteArray):BridgeRequestWire? = try {
        json.decodeFromString<BridgeRequestWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }

    @OptIn(InternalSerializationApi::class)
    fun encodeResponse(wire:BridgeResponseWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)

    @OptIn(InternalSerializationApi::class)
    fun decodeResponse(bytes:ByteArray):BridgeResponseWire? = try {
        json.decodeFromString<BridgeResponseWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }
}
