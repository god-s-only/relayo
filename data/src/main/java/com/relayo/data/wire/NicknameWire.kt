package com.relayo.data.wire

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@InternalSerializationApi
data class NicknameWire(
    val senderId:String,
    val nickname:String,
    val fingerprint:String,
    val timestampEpochMillis:Long
)

object NicknameWireCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(InternalSerializationApi::class)
    fun encode(wire:NicknameWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)

    @OptIn(InternalSerializationApi::class)
    fun decode(bytes:ByteArray):NicknameWire? = try {
        json.decodeFromString<NicknameWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }
}
