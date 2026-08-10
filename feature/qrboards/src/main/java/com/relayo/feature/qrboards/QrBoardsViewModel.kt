package com.relayo.feature.qrboards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.domain.model.Board
import com.relayo.domain.model.BoardPost
import com.relayo.domain.usecase.CreateBoardUseCase
import com.relayo.domain.usecase.JoinBoardUseCase
import com.relayo.domain.usecase.ObserveBoardPostsUseCase
import com.relayo.domain.usecase.ObserveMyBoardsUseCase
import com.relayo.domain.usecase.PostToBoardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QrBoardsUiState(
    val boards:List<Board> = emptyList(),
    val isCreateDialogOpen:Boolean = false,
    val newBoardName:String = "",
    val isScannerOpen:Boolean = false,
    val selectedBoardId:String? = null,
    val selectedBoardPosts:List<BoardPost> = emptyList(),
    val postDraft:String = ""
)

@HiltViewModel
class QrBoardsViewModel @Inject constructor(
    observeMyBoards:ObserveMyBoardsUseCase,
    private val observeBoardPosts:ObserveBoardPostsUseCase,
    private val createBoard:CreateBoardUseCase,
    private val joinBoard:JoinBoardUseCase,
    private val postToBoard:PostToBoardUseCase
):ViewModel() {

    private val _uiState = MutableStateFlow(QrBoardsUiState())
    val uiState:StateFlow<QrBoardsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeMyBoards().collect { boards ->
                _uiState.value = _uiState.value.copy(boards = boards)
            }
        }
    }

    fun onCreateDialogToggled() {
        _uiState.value = _uiState.value.copy(isCreateDialogOpen = !_uiState.value.isCreateDialogOpen)
    }

    fun onNewBoardNameChanged(name:String) {
        _uiState.value = _uiState.value.copy(newBoardName = name)
    }

    fun onCreateBoardConfirmed() {
        val name = _uiState.value.newBoardName.trim()
        if(name.isEmpty()) return
        viewModelScope.launch {
            val board = createBoard(name)
            _uiState.value = _uiState.value.copy(
                isCreateDialogOpen = false,
                newBoardName = "",
                selectedBoardId = board.id
            )
            observePosts(board.id)
        }
    }

    fun onScannerToggled() {
        _uiState.value = _uiState.value.copy(isScannerOpen = !_uiState.value.isScannerOpen)
    }

    fun onQrScanned(scannedBoardId:String) {
        viewModelScope.launch {
            val board = joinBoard(scannedBoardId)
            _uiState.value = _uiState.value.copy(
                isScannerOpen = false,
                selectedBoardId = board?.id
            )
            if(board != null) observePosts(board.id)
        }
    }

    fun onBoardSelected(boardId:String) {
        _uiState.value = _uiState.value.copy(selectedBoardId = boardId)
        observePosts(boardId)
    }

    fun onBoardDetailClosed() {
        _uiState.value = _uiState.value.copy(selectedBoardId = null, selectedBoardPosts = emptyList())
    }

    fun onPostDraftChanged(text:String) {
        _uiState.value = _uiState.value.copy(postDraft = text)
    }

    fun onPostSubmitted() {
        val boardId = _uiState.value.selectedBoardId ?: return
        val text = _uiState.value.postDraft.trim()
        if(text.isEmpty()) return
        _uiState.value = _uiState.value.copy(postDraft = "")
        viewModelScope.launch {
            postToBoard(boardId, text)
        }
    }

    private fun observePosts(boardId:String) {
        viewModelScope.launch {
            observeBoardPosts(boardId).collect { posts ->
                if(_uiState.value.selectedBoardId == boardId) {
                    _uiState.value = _uiState.value.copy(selectedBoardPosts = posts)
                }
            }
        }
    }
}