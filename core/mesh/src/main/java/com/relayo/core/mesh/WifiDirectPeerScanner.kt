package com.relayo.core.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import com.relayo.core.transport.DiscoveredPeer
import com.relayo.core.transport.PeerScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiDirectPeerScanner @Inject constructor(
    @ApplicationContext private val context:Context
):PeerScanner {

    private val manager:WifiP2pManager? by lazy {
        try { context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager } catch(_:Exception) { null }
    }
    private var channel:WifiP2pManager.Channel? = null

    private fun getChannel():WifiP2pManager.Channel? {
        if(channel != null) return channel
        val mgr = manager ?: return null
        return try {
            mgr.initialize(context, context.mainLooper, null).also { channel = it }
        } catch(_:Exception) { null }
    }

    @SuppressLint("MissingPermission")
    override fun scan():Flow<DiscoveredPeer> = callbackFlow {
        val mgr = manager
        val ch = getChannel()
        if(mgr == null || ch == null) {
            // WiFi Direct not available — emit nothing and close gracefully
            awaitClose {}
            return@callbackFlow
        }
        // Kick off discovery
        try { mgr.discoverPeers(ch, null) } catch(_:SecurityException) {} catch(_:Exception) {}

        val job = launch {
            while(true) {
                try {
                    mgr.requestPeers(ch) { peerList ->
                        peerList.deviceList.forEach { device:WifiP2pDevice ->
                            trySend(
                                DiscoveredPeer(
                                    address = device.deviceAddress,
                                    name = device.deviceName,
                                    rssi = 0
                                )
                            )
                        }
                    }
                } catch(_:SecurityException) {} catch(_:Exception) {}
                delay(5000)
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    override fun startAdvertising() {
        val mgr = manager ?: return
        val ch = getChannel() ?: return
        try { mgr.createGroup(ch, null) } catch(_:SecurityException) {} catch(_:Exception) {}
    }

    @SuppressLint("MissingPermission")
    override fun stopAdvertising() {
        val mgr = manager ?: return
        val ch = getChannel() ?: return
        try { mgr.removeGroup(ch, null) } catch(_:SecurityException) {} catch(_:Exception) {}
    }
}
