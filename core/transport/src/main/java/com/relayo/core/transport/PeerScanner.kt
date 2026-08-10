package com.relayo.core.transport

import kotlinx.coroutines.flow.Flow

interface PeerScanner {
    fun scan():Flow<DiscoveredPeer>
    fun startAdvertising()
    fun stopAdvertising()
}