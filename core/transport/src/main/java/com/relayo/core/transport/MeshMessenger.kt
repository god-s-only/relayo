package com.relayo.core.transport

import kotlinx.coroutines.flow.Flow

data class IncomingBytes(
    val fromAddress:String,
    val payload:ByteArray
)

interface MeshMessenger {
    fun observeIncoming():Flow<IncomingBytes>
    suspend fun sendTo(address:String, payload:ByteArray):Boolean
}