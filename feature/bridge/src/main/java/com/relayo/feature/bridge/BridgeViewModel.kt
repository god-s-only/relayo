package com.relayo.feature.bridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.domain.model.BridgeRequest
import com.relayo.domain.model.BridgeRequestType
import com.relayo.domain.model.BridgeResponse
import com.relayo.domain.repository.BridgeRepository
import com.relayo.domain.usecase.ObserveBridgeRequestsUseCase
import com.relayo.domain.usecase.ObserveBridgeResponsesUseCase
import com.relayo.domain.usecase.SendBridgeRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BridgeUiState(
    val requests:List<BridgeRequest> = emptyList(),
    val responses:List<BridgeResponse> = emptyList(),
    val query:String = "",
    val selectedType:BridgeRequestType = BridgeRequestType.WEB_FETCH,
    val hasInternet:Boolean = false,
    val isSending:Boolean = false,
    val error:String? = null
)

@HiltViewModel
class BridgeViewModel @Inject constructor(
    observeRequests:ObserveBridgeRequestsUseCase,
    observeResponses:ObserveBridgeResponsesUseCase,
    private val sendRequest:SendBridgeRequestUseCase,
    private val bridgeRepository:BridgeRepository
):ViewModel() {

    private val _uiState = MutableStateFlow(BridgeUiState())
    val uiState:StateFlow<BridgeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeRequests().collect { reqs ->
                _uiState.value = _uiState.value.copy(requests = reqs)
            }
        }
        viewModelScope.launch {
            observeResponses().collect { resps ->
                _uiState.value = _uiState.value.copy(responses = resps)
            }
        }
        viewModelScope.launch {
            val hasNet = try { bridgeRepository.hasInternet() } catch(_:Exception){ false }
            _uiState.value = _uiState.value.copy(hasInternet = hasNet)
        }
    }

    fun onQueryChanged(text:String) {
        _uiState.value = _uiState.value.copy(query = text, error = null)
    }

    fun onTypeSelected(type:BridgeRequestType) {
        _uiState.value = _uiState.value.copy(selectedType = type)
    }

    fun onSendClicked() {
        val query = _uiState.value.query.trim()
        if(query.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Query cannot be empty")
            return
        }
        val type = _uiState.value.selectedType
        _uiState.value = _uiState.value.copy(isSending = true, error = null)
        viewModelScope.launch {
            try {
                sendRequest(type, query)
                _uiState.value = _uiState.value.copy(query = "", isSending = false)
            } catch(e:IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(isSending = false, error = e.message)
            } catch(e:Exception) {
                _uiState.value = _uiState.value.copy(isSending = false, error = e.message ?: "Failed to send")
            }
        }
    }

    fun getResponseFor(requestId:String):BridgeResponse? =
        _uiState.value.responses.find { it.requestId == requestId }
}
