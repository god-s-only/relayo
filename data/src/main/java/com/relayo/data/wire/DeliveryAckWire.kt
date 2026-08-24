package com.relayo.data.wire

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@InternalSerializationApi
data class DeliveryAckWire(
    val originalMessageId:String,
    val ackSenderId:String,
    val originalSenderId:String,
    val timestampEpochMillis:Long
)

object DeliveryAckWireCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(InternalSerializationApi::class)
    fun encode(wire:DeliveryAckWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)

    @OptIn(InternalSerializationApi::class)
    fun decode(bytes:ByteArray):DeliveryAckWire? = try {
        json.decodeFromString<DeliveryAckWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }
}
