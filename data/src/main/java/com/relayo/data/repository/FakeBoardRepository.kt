package com.relayo.data.repository

import com.relayo.domain.filter.ContentFilter
import com.relayo.domain.model.Board
import com.relayo.domain.model.BoardPost
import com.relayo.domain.repository.BoardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeBoardRepository @Inject constructor(
    private val contentFilter:ContentFilter
):BoardRepository {

    private val allBoards = mutableMapOf<String, Board>()

    private val _myBoards = MutableStateFlow<List<Board>>(emptyList())
    private val myBoardsFlow:StateFlow<List<Board>> = _myBoards.asStateFlow()

    private val postsByBoard = mutableMapOf<String, MutableStateFlow<List<BoardPost>>>()

    private fun postsFlowFor(boardId:String):MutableStateFlow<List<BoardPost>> =
        postsByBoard.getOrPut(boardId) { MutableStateFlow(emptyList()) }

    override fun observeMyBoards() = myBoardsFlow

    override fun observeBoardPosts(boardId:String) = postsFlowFor(boardId).asStateFlow()

    override suspend fun createBoard(name:String):Board {
        val safeName = if(contentFilter.isAllowed(name)) name else contentFilter.sanitize(name).ifBlank { "Board" }
        val board = Board(
            id = "board-${System.nanoTime()}",
            name = safeName,
            createdByDisplayName = "You",
            createdEpochMillis = System.currentTimeMillis()
        )
        allBoards[board.id] = board
        _myBoards.value = _myBoards.value + board
        return board
    }

    override suspend fun joinBoard(boardId:String):Board? {
        val existing = allBoards[boardId]
        if(existing != null) {
            if(_myBoards.value.none { it.id == boardId }) {
                _myBoards.value = _myBoards.value + existing
            }
            return existing
        }
        val joined = Board(
            id = boardId,
            name = "Scanned Board",
            createdByDisplayName = "Unknown",
            createdEpochMillis = System.currentTimeMillis()
        )
        allBoards[boardId] = joined
        _myBoards.value = _myBoards.value + joined
        return joined
    }

    override suspend fun postToBoard(boardId:String, content:String) {
        if(!contentFilter.isAllowed(content)) return
        val post = BoardPost(
            id = "post-${System.nanoTime()}",
            boardId = boardId,
            authorDisplayName = "You",
            content = content,
            timestampEpochMillis = System.currentTimeMillis()
        )
        postsFlowFor(boardId).value = postsFlowFor(boardId).value + post
    }
}