package com.relayo.data.wire

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
@InternalSerializationApi
data class KeyExchangeWire(
    val senderId:String,
    val publicKeyBase64:String,
    val timestampEpochMillis:Long,
    val sessionId:String = ""
)

object KeyExchangeWireCodec {
    private val json = Json { ignoreUnknownKeys = true }
    @OptIn(InternalSerializationApi::class)
    fun encode(wire:KeyExchangeWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)
    @OptIn(InternalSerializationApi::class)
    fun decode(bytes:ByteArray):KeyExchangeWire? = try {
        json.decodeFromString<KeyExchangeWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }
}
