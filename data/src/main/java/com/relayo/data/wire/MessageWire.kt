package com.relayo.data.wire

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@InternalSerializationApi
data class MessageWire(
    val messageId:String = "",
    val senderId:String,
    val recipientId:String,
    val ivBase64:String,
    val cipherBase64:String,
    val timestampEpochMillis:Long
)

object MessageWireCodec {
    private val json = Json { ignoreUnknownKeys = true }
    @OptIn(InternalSerializationApi::class)
    fun encode(wire:MessageWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)
    @OptIn(InternalSerializationApi::class)
    fun decode(bytes:ByteArray):MessageWire? = try {
        json.decodeFromString<MessageWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }
}