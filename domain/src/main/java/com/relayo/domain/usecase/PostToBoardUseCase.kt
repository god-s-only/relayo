package com.relayo.domain.usecase

import com.relayo.domain.repository.BoardRepository
import javax.inject.Inject

class PostToBoardUseCase @Inject constructor(
    private val repository:BoardRepository
) {
    suspend operator fun invoke(boardId:String, content:String) {
        repository.postToBoard(boardId, content)
    }
}