package com.relayo.feature.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.domain.model.Message
import com.relayo.domain.usecase.ObserveConversationUseCase
import com.relayo.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessagesUiState(
    val messages:List<Message> = emptyList(),
    val draft:String = ""
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    savedStateHandle:SavedStateHandle,
    observeConversation:ObserveConversationUseCase,
    private val sendMessage:SendMessageUseCase
):ViewModel() {

    private val peerId:String = checkNotNull(savedStateHandle["peerId"])

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState:StateFlow<MessagesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeConversation(peerId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    fun onDraftChanged(text:String) {
        _uiState.value = _uiState.value.copy(draft = text)
    }

    fun onSendClicked() {
        val text = _uiState.value.draft.trim()
        if(text.isEmpty()) return
        _uiState.value = _uiState.value.copy(draft = "")
        viewModelScope.launch {
            sendMessage(peerId, text)
        }
    }
}