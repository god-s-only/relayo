package com.relayo.domain.usecase

import com.relayo.domain.model.Board
import com.relayo.domain.repository.BoardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMyBoardsUseCase @Inject constructor(
    private val repository:BoardRepository
) {
    operator fun invoke():Flow<List<Board>> = repository.observeMyBoards()
}