package com.relayo.domain.usecase

import com.relayo.domain.model.Board
import com.relayo.domain.repository.BoardRepository
import javax.inject.Inject

class JoinBoardUseCase @Inject constructor(
    private val repository:BoardRepository
) {
    suspend operator fun invoke(boardId:String):Board? = repository.joinBoard(boardId)
}