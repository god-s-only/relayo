package com.relayo.core.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import com.relayo.core.transport.IncomingBytes
import com.relayo.core.transport.MeshMessenger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiDirectMeshMessenger @Inject constructor(
    @ApplicationContext private val context:Context
):MeshMessenger {

    private val _incoming = MutableSharedFlow<IncomingBytes>(extraBufferCapacity = 32)
    private val messengerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket:ServerSocket? = null
    private var isStarted = false
    private val peerIpMap = mutableMapOf<String, String>()
    private val peerIpLock = Any()

    companion object {
        const val WIFI_DIRECT_PORT = 8888
    }

    override fun observeIncoming() = _incoming.asSharedFlow()

    @SuppressLint("MissingPermission")
    override fun start() {
        if(isStarted) return
        isStarted = true
        messengerScope.launch {
            try {
                val ss = ServerSocket(WIFI_DIRECT_PORT)
                serverSocket = ss
                while(!ss.isClosed) {
                    val client = ss.accept()
                    launch {
                        try {
                            val dis = DataInputStream(client.getInputStream())
                            val len = dis.readInt()
                            if(len <= 0 || len > 1024 * 1024) {
                                client.close()
                                return@launch
                            }
                            val payload = ByteArray(len)
                            dis.readFully(payload)
                            val fromAddress = client.inetAddress.hostAddress ?: "wifi-direct"
                            _incoming.tryEmit(IncomingBytes(fromAddress = fromAddress, payload = payload))
                            client.close()
                        } catch(_:Exception) {
                            try { client.close() } catch(_:Exception) {}
                        }
                    }
                }
            } catch(_:Exception) {}
        }

        // Try to populate peer IPs via WiFi Direct connection info
        try {
            val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            val channel = manager?.initialize(context, context.mainLooper, null)
            if(manager != null && channel != null) {
                messengerScope.launch(Dispatchers.Main) {
                    try {
                        manager.requestConnectionInfo(channel) { info ->
                            info?.groupOwnerAddress?.hostAddress?.let { goAddress ->
                                synchronized(peerIpLock) {
                                    // Store group owner IP for all peers (simplified)
                                    peerIpMap["groupOwner"] = goAddress
                                }
                            }
                        }
                    } catch(_:SecurityException) {} catch(_:Exception) {}
                }
            }
        } catch(_:Exception) {}
    }

    fun updatePeerIp(deviceAddress:String, ipAddress:String) {
        synchronized(peerIpLock) { peerIpMap[deviceAddress] = ipAddress }
    }

    override suspend fun sendTo(address:String, payload:ByteArray):Boolean = withContext(Dispatchers.IO) {
        if(!isStarted) return@withContext false
        val ip = synchronized(peerIpLock) { peerIpMap[address] ?: peerIpMap["groupOwner"] }
        if(ip == null) return@withContext false
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, WIFI_DIRECT_PORT), 3000)
            val dos = DataOutputStream(socket.getOutputStream())
            dos.writeInt(payload.size)
            dos.write(payload)
            dos.flush()
            socket.close()
            true
        } catch(_:Exception) {
            false
        }
    }
}
