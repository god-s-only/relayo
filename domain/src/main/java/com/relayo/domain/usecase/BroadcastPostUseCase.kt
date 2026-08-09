package com.relayo.domain.usecase

import com.relayo.domain.repository.NewsFeedRepository
import javax.inject.Inject

class BroadcastPostUseCase @Inject constructor(
    private val repository:NewsFeedRepository
) {
    suspend operator fun invoke(content:String) = repository.broadcastPost(content)
}