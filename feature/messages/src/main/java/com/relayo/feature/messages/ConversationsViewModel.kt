package com.relayo.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.domain.model.ConversationSummary
import com.relayo.domain.usecase.ObserveConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    observeConversations:ObserveConversationsUseCase
):ViewModel() {

    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations:StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()

    init {
        viewModelScope.launch {
            observeConversations().collect { list ->
                _conversations.value = list
            }
        }
    }
}