package com.relayo.domain.model

data class BridgeResponse(
    val requestId:String,
    val responderId:String,
    val result:String,
    val error:String? = null,
    val timestampEpochMillis:Long
)
