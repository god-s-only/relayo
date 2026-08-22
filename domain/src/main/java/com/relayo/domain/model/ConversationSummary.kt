package com.relayo.domain.model

data class ConversationSummary(
    val peerId:String,
    val displayName:String,
    val lastMessagePreview:String?,
    val lastMessageTimestampEpochMillis:Long?,
    val isOnline:Boolean
)