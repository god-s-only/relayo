package com.relayo.domain.model

data class Board(
    val id:String,
    val name:String,
    val createdByDisplayName:String,
    val createdEpochMillis:Long
)