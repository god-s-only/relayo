package com.relayo.domain.repository

import com.relayo.domain.model.NewsPost
import kotlinx.coroutines.flow.Flow

interface NewsFeedRepository {
    fun observeFeed():Flow<List<NewsPost>>
    suspend fun broadcastPost(content:String)
}