package com.relayo.domain.model

data class BridgeRequest(
    val id:String,
    val requesterId:String,
    val type:BridgeRequestType,
    val query:String,
    val timestampEpochMillis:Long
)
