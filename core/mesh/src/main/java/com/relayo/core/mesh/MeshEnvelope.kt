package com.relayo.core.mesh

import kotlinx.serialization.Serializable

@Serializable
data class MeshEnvelope(
    val messageId:String,
    val originAddress:String,
    val ttl:Int,
    val payloadType:String,
    val payloadBytes:ByteArray
) {
    override fun equals(other:Any?):Boolean {
        if(this === other) return true
        if(other !is MeshEnvelope) return false
        return messageId == other.messageId
    }

    override fun hashCode():Int = messageId.hashCode()
}