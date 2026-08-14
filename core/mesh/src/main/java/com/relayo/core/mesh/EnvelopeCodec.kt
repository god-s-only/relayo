package com.relayo.core.mesh

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object EnvelopeCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(envelope:MeshEnvelope):ByteArray =
        json.encodeToString(envelope).toByteArray(Charsets.UTF_8)

    fun decode(bytes:ByteArray):MeshEnvelope? = try {
        json.decodeFromString<MeshEnvelope>(String(bytes, Charsets.UTF_8))
    } catch(e:Exception) {
        null
    }
}