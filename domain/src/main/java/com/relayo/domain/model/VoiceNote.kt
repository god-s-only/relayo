package com.relayo.domain.model

data class VoiceNote(
    val id:String,
    val senderId:String,
    val filePath:String,
    val durationMillis:Long,
    val timestampEpochMillis:Long,
    val isFromMe:Boolean
)