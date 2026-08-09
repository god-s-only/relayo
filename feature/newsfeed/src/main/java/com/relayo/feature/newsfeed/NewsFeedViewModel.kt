package com.relayo.feature.newsfeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.domain.model.NewsPost
import com.relayo.domain.usecase.BroadcastPostUseCase
import com.relayo.domain.usecase.ObserveNewsFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsFeedUiState(
    val posts:List<NewsPost> = emptyList(),
    val composerText:String = "",
    val isComposerOpen:Boolean = false
)

@HiltViewModel
class NewsFeedViewModel @Inject constructor(
    observeFeed:ObserveNewsFeedUseCase,
    private val broadcastPost:BroadcastPostUseCase
):ViewModel() {

    private val _uiState = MutableStateFlow(NewsFeedUiState())
    val uiState:StateFlow<NewsFeedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeFeed().collect { posts ->
                _uiState.value = _uiState.value.copy(posts = posts)
            }
        }
    }

    fun onComposerToggled() {
        _uiState.value = _uiState.value.copy(isComposerOpen = !_uiState.value.isComposerOpen)
    }

    fun onComposerTextChanged(text:String) {
        _uiState.value = _uiState.value.copy(composerText = text)
    }

    fun onBroadcastClicked() {
        val text = _uiState.value.composerText.trim()
        if(text.isEmpty()) return
        viewModelScope.launch {
            broadcastPost(text)
        }
        _uiState.value = _uiState.value.copy(composerText = "", isComposerOpen = false)
    }
}