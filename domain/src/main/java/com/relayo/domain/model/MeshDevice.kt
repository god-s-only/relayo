package com.relayo.domain.model

data class MeshDevice(
    val id:String,
    val displayName:String,
    val hopCount:Int,
    val signalStrength:Int,
    val lastSeenEpochMillis:Long,
    val isDirectNeighbor:Boolean,
    val fingerprint:String? = null
)