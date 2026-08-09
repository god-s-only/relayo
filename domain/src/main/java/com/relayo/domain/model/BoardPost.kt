package com.relayo.domain.model

data class BoardPost(
    val id:String,
    val boardId:String,
    val authorDisplayName:String,
    val content:String,
    val timestampEpochMillis:Long
)