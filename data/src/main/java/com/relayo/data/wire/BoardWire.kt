package com.relayo.data.wire

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
@InternalSerializationApi
data class BoardWire(
    val id:String,
    val name:String,
    val createdByDisplayName:String,
    val createdEpochMillis:Long
)

@Serializable
@InternalSerializationApi
data class BoardPostWire(
    val id:String,
    val boardId:String,
    val authorDisplayName:String,
    val content:String,
    val timestampEpochMillis:Long
)

object BoardWireCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(InternalSerializationApi::class)
    fun encodeBoard(wire:BoardWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)

    @OptIn(InternalSerializationApi::class)
    fun decodeBoard(bytes:ByteArray):BoardWire? = try {
        json.decodeFromString<BoardWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }

    @OptIn(InternalSerializationApi::class)
    fun encodePost(wire:BoardPostWire):ByteArray = json.encodeToString(wire).toByteArray(Charsets.UTF_8)

    @OptIn(InternalSerializationApi::class)
    fun decodePost(bytes:ByteArray):BoardPostWire? = try {
        json.decodeFromString<BoardPostWire>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) { null }
}
