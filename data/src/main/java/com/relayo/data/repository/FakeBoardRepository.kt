package com.relayo.data.repository

import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.wire.BoardPostWire
import com.relayo.data.wire.BoardWire
import com.relayo.data.wire.BoardWireCodec
import com.relayo.domain.filter.ContentFilter
import com.relayo.domain.model.Board
import com.relayo.domain.model.BoardPost
import com.relayo.domain.repository.BoardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import javax.inject.Inject
import javax.inject.Singleton

private const val PAYLOAD_TYPE_BOARD_CREATE = "board_create"
private const val PAYLOAD_TYPE_BOARD_POST = "board_post"

@OptIn(InternalSerializationApi::class)
@Singleton
class FakeBoardRepository @Inject constructor(
    private val contentFilter:ContentFilter,
    private val floodRouter:MeshFloodRouter
):BoardRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val allBoards = mutableMapOf<String, Board>()

    private val _myBoards = MutableStateFlow<List<Board>>(emptyList())
    private val myBoardsFlow:StateFlow<List<Board>> = _myBoards.asStateFlow()

    private val postsByBoard = mutableMapOf<String, MutableStateFlow<List<BoardPost>>>()

    private fun postsFlowFor(boardId:String):MutableStateFlow<List<BoardPost>> =
        postsByBoard.getOrPut(boardId) { MutableStateFlow(emptyList()) }

    init {
        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                when(received.payloadType) {
                    PAYLOAD_TYPE_BOARD_CREATE -> handleIncomingBoard(received.payloadBytes)
                    PAYLOAD_TYPE_BOARD_POST -> handleIncomingPost(received.payloadBytes)
                }
            }
        }
    }

    private fun handleIncomingBoard(payloadBytes:ByteArray) {
        val wire = BoardWireCodec.decodeBoard(payloadBytes) ?: return
        if(!contentFilter.isAllowed(wire.name)) return
        if(allBoards.containsKey(wire.id)) return
        val board = Board(
            id = wire.id,
            name = wire.name,
            createdByDisplayName = wire.createdByDisplayName,
            createdEpochMillis = wire.createdEpochMillis
        )
        allBoards[board.id] = board
        // Do not auto-add to _myBoards — user must still scan/join to become member
    }

    private fun handleIncomingPost(payloadBytes:ByteArray) {
        val wire = BoardWireCodec.decodePost(payloadBytes) ?: return
        if(!contentFilter.isAllowed(wire.content)) return
        // Ensure board exists (create placeholder if not previously announced)
        if(!allBoards.containsKey(wire.boardId)) {
            allBoards[wire.boardId] = Board(
                id = wire.boardId,
                name = "Board ${wire.boardId.takeLast(6)}",
                createdByDisplayName = "Unknown",
                createdEpochMillis = wire.timestampEpochMillis
            )
        }
        val post = BoardPost(
            id = wire.id,
            boardId = wire.boardId,
            authorDisplayName = wire.authorDisplayName,
            content = wire.content,
            timestampEpochMillis = wire.timestampEpochMillis
        )
        val flow = postsFlowFor(wire.boardId)
        if(flow.value.any { it.id == post.id }) return
        flow.value = flow.value + post
    }

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
        // Announce to mesh
        try {
            val wire = BoardWire(
                id = board.id,
                name = board.name,
                createdByDisplayName = board.createdByDisplayName,
                createdEpochMillis = board.createdEpochMillis
            )
            floodRouter.broadcast(PAYLOAD_TYPE_BOARD_CREATE, BoardWireCodec.encodeBoard(wire))
        } catch(_:Exception) {}
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
        // Broadcast to mesh for other members
        try {
            val wire = BoardPostWire(
                id = post.id,
                boardId = post.boardId,
                authorDisplayName = post.authorDisplayName,
                content = post.content,
                timestampEpochMillis = post.timestampEpochMillis
            )
            floodRouter.broadcast(PAYLOAD_TYPE_BOARD_POST, BoardWireCodec.encodePost(wire))
        } catch(_:Exception) {}
    }
}
