package com.relayo.domain.usecase

import com.relayo.domain.model.NewsPost
import com.relayo.domain.repository.NewsFeedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNewsFeedUseCase @Inject constructor(
    private val repository:NewsFeedRepository
) {
    operator fun invoke():Flow<List<NewsPost>> = repository.observeFeed()
}