package com.relayo.core.mesh

import com.relayo.core.transport.DiscoveredPeer
import com.relayo.core.transport.PeerScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridPeerScanner @Inject constructor(
    private val bleScanner:BlePeerScanner,
    private val wifiScanner:WifiDirectPeerScanner
):PeerScanner {

    override fun scan():Flow<DiscoveredPeer> = merge(
        bleScanner.scan(),
        wifiScanner.scan()
    )

    override fun startAdvertising() {
        try { bleScanner.startAdvertising() } catch(_:Exception) {}
        try { wifiScanner.startAdvertising() } catch(_:Exception) {}
    }

    override fun stopAdvertising() {
        try { bleScanner.stopAdvertising() } catch(_:Exception) {}
        try { wifiScanner.stopAdvertising() } catch(_:Exception) {}
    }
}
