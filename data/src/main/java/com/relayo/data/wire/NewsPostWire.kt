package com.relayo.data.wire

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@InternalSerializationApi
data class NewsPostWire(
    val authorId:String,
    val authorDisplayName:String,
    val content:String,
    val timestampEpochMillis:Long
)

object NewsPostWireCodec {
    private val json = Json { ignoreUnknownKeys = true }
    @OptIn(InternalSerializationApi::class)
    fun encode(wire:NewsPostWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)
    @OptIn(InternalSerializationApi::class)
    fun decode(bytes:ByteArray):NewsPostWire? = try {
        json.decodeFromString<NewsPostWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }
}