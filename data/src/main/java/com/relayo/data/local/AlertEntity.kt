package com.relayo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id:String,
    val authorId:String,
    val authorDisplayName:String,
    val message:String,
    val severity:String,
    val timestampEpochMillis:Long,
    val hopCount:Int
)
