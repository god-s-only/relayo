package com.relayo.domain.model

data class EphemeralIdentity(
    val sessionId:String,
    val publicKeyBytes:ByteArray,
    val privateKeyBytes:ByteArray
){
    override fun equals(other:Any?):Boolean {
        if(this === other) return true
        if(other !is EphemeralIdentity) return false
        return sessionId == other.sessionId
    }

    override fun hashCode():Int = sessionId.hashCode()

    override fun toString():String = "EphemeralIdentity(sessionId=$sessionId, publicKeyBytes=****, privateKeyBytes=****)"
}
