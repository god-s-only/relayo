package com.relayo.domain.usecase

import com.relayo.domain.model.BoardPost
import com.relayo.domain.repository.BoardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBoardPostsUseCase @Inject constructor(
    private val repository:BoardRepository
) {
    operator fun invoke(boardId:String):Flow<List<BoardPost>> = repository.observeBoardPosts(boardId)
}