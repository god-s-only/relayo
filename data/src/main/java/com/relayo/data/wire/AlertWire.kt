package com.relayo.data.wire

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
@InternalSerializationApi
data class AlertWire(
    val authorId:String,
    val authorDisplayName:String,
    val message:String,
    val severity:String,
    val timestampEpochMillis:Long
)

object AlertWireCodec {
    private val json = Json { ignoreUnknownKeys = true }
    @OptIn(InternalSerializationApi::class)
    fun encode(wire:AlertWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)
    @OptIn(InternalSerializationApi::class)
    fun decode(bytes:ByteArray):AlertWire? = try {
        json.decodeFromString<AlertWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }
}