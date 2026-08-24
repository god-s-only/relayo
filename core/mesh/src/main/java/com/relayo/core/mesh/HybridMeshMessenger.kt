package com.relayo.core.mesh

import com.relayo.core.transport.IncomingBytes
import com.relayo.core.transport.MeshMessenger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridMeshMessenger @Inject constructor(
    private val bleMessenger:BleMeshMessenger,
    private val wifiMessenger:WifiDirectMeshMessenger
):MeshMessenger {

    override fun start() {
        try { bleMessenger.start() } catch(_:Exception) {}
        try { wifiMessenger.start() } catch(_:Exception) {}
    }

    override fun observeIncoming():Flow<IncomingBytes> = merge(
        bleMessenger.observeIncoming(),
        wifiMessenger.observeIncoming()
    )

    override suspend fun sendTo(address:String, payload:ByteArray):Boolean {
        // Try WiFi Direct first for higher throughput (voice notes chunking)
        // Fall back to BLE if WiFi fails or not available
        return try {
            if(wifiMessenger.sendTo(address, payload)) true
            else bleMessenger.sendTo(address, payload)
        } catch(_:Exception) {
            try { bleMessenger.sendTo(address, payload) } catch(_:Exception) { false }
        }
    }
}
