package com.relayo.core.transport

data class DiscoveredPeer(
    val address:String,
    val name:String?,
    val rssi:Int
)