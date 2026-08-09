package com.relayo.domain.model

data class NewsPost(
    val id:String,
    val authorId:String,
    val authorDisplayName:String,
    val content:String,
    val timestampEpochMillis:Long,
    val hopCount:Int
)