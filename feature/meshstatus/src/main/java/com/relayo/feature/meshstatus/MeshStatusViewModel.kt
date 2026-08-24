package com.relayo.feature.meshstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.domain.model.MeshDevice
import com.relayo.domain.repository.MeshRepository
import com.relayo.domain.usecase.GenerateIdentityUseCase
import com.relayo.domain.usecase.ObserveMyNicknameUseCase
import com.relayo.domain.usecase.ObserveNearbyDevicesUseCase
import com.relayo.domain.usecase.SetMyNicknameUseCase
import com.relayo.domain.usecase.WipeIdentityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeshStatusUiState(
    val devices:List<MeshDevice> = emptyList(),
    val isLoading:Boolean = true,
    val sessionId:String? = null,
    val lastReceivedBroadcast:String? = null,
    val myNickname:String? = null,
    val nicknameDraft:String = ""
)

@HiltViewModel
class MeshStatusViewModel @Inject constructor(
    observeNearbyDevices:ObserveNearbyDevicesUseCase,
    private val generateIdentity:GenerateIdentityUseCase,
    private val wipeIdentity:WipeIdentityUseCase,
    meshRepository:MeshRepository,
    private val floodRouter:MeshFloodRouter,
    observeMyNickname:ObserveMyNicknameUseCase,
    private val setMyNickname:SetMyNicknameUseCase
):ViewModel() {

    private val _uiState = MutableStateFlow(MeshStatusUiState())
    val uiState:StateFlow<MeshStatusUiState> = _uiState.asStateFlow()

    init {
        regenerateIdentity()
        viewModelScope.launch {
            observeNearbyDevices().collect { devices ->
                _uiState.value = _uiState.value.copy(
                    devices = devices,
                    isLoading = false
                )
            }
        }
        viewModelScope.launch {
            observeMyNickname().collect { nick ->
                _uiState.value = _uiState.value.copy(myNickname = nick)
            }
        }
        viewModelScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                if(received.payloadType == "test") {
                    val text = String(received.payloadBytes)
                    _uiState.value = _uiState.value.copy(lastReceivedBroadcast = text)
                    android.util.Log.d("RelayoDebug", "Flood-received: $text")
                }
            }
        }
    }

    private fun regenerateIdentity() {
        viewModelScope.launch {
            val identity = generateIdentity()
            _uiState.value = _uiState.value.copy(sessionId = identity.sessionId)
        }
    }

    fun onEmergencyWipe() {
        viewModelScope.launch {
            wipeIdentity()
            _uiState.value = _uiState.value.copy(sessionId = null)
            regenerateIdentity()
        }
    }

    fun onDebugFloodBroadcast() {
        viewModelScope.launch {
            floodRouter.broadcast("test", "Hello mesh".toByteArray())
            android.util.Log.d("RelayoDebug", "Flood-broadcast sent")
        }
    }

    fun onPermissionsGranted() {
        // Mesh is started via AppSessionViewModel / RelayoNavHost; keep for UI compat
    }

    fun onNicknameDraftChanged(text:String) {
        _uiState.value = _uiState.value.copy(nicknameDraft = text)
    }

    fun onSetNickname() {
        val nick = _uiState.value.nicknameDraft.trim()
        if(nick.isEmpty()) return
        viewModelScope.launch {
            setMyNickname(nick)
            _uiState.value = _uiState.value.copy(nicknameDraft = "")
        }
    }
}