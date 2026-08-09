package com.relayo.domain.usecase

import com.relayo.domain.model.Board
import com.relayo.domain.repository.BoardRepository
import javax.inject.Inject

class CreateBoardUseCase @Inject constructor(
    private val repository:BoardRepository
) {
    suspend operator fun invoke(name:String):Board = repository.createBoard(name)
}