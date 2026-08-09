package com.relayo.domain.repository

import com.relayo.domain.model.Board
import com.relayo.domain.model.BoardPost
import kotlinx.coroutines.flow.Flow

interface BoardRepository {
    fun observeMyBoards():Flow<List<Board>>
    fun observeBoardPosts(boardId:String):Flow<List<BoardPost>>
    suspend fun createBoard(name:String):Board
    suspend fun joinBoard(boardId:String):Board?
    suspend fun postToBoard(boardId:String, content:String)
}