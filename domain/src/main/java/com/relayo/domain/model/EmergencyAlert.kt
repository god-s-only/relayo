package com.relayo.domain.model

data class EmergencyAlert(
    val id:String,
    val authorId:String,
    val authorDisplayName:String,
    val message:String,
    val severity:AlertSeverity,
    val timestampEpochMillis:Long,
    val hopCount:Int
)