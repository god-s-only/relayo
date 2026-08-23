package com.relayo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_posts")
data class NewsPostEntity(
    @PrimaryKey val id:String,
    val authorId:String,
    val authorDisplayName:String,
    val content:String,
    val timestampEpochMillis:Long,
    val hopCount:Int
)
