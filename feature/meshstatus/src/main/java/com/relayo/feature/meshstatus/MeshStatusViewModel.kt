package com.relayo.feature.meshstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.domain.model.MeshDevice
import com.relayo.domain.usecase.GenerateIdentityUseCase
import com.relayo.domain.usecase.ObserveNearbyDevicesUseCase
import com.relayo.domain.usecase.WipeIdentityUseCase
import com.relayo.domain.repository.MeshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.relayo.core.transport.MeshMessenger
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeshStatusUiState(
    val devices:List<MeshDevice> = emptyList(),
    val isLoading:Boolean = true,
    val sessionId:String? = null,
    val isDiscoveryActive:Boolean = false
)

@HiltViewModel
class MeshStatusViewModel @Inject constructor(
    observeNearbyDevices:ObserveNearbyDevicesUseCase,
    private val generateIdentity:GenerateIdentityUseCase,
    private val wipeIdentity:WipeIdentityUseCase,
    private val meshRepository:MeshRepository,
    private val messenger:MeshMessenger
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

    fun onPermissionsGranted() {
        if(_uiState.value.isDiscoveryActive) return
        _uiState.value = _uiState.value.copy(isDiscoveryActive = true)
        messenger.start()
        viewModelScope.launch {
            meshRepository.startDiscovery()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            meshRepository.stopDiscovery()
        }
    }


    fun onDebugSendTapped() {
        val targetAddress = _uiState.value.devices.firstOrNull()?.id ?: return
        viewModelScope.launch {
            val success = messenger.sendTo(targetAddress, "Hello from Relayo".toByteArray())
            android.util.Log.d("RelayoDebug", "Send to $targetAddress succeeded=$success")
        }
        viewModelScope.launch {
            messenger.observeIncoming().collect { incoming ->
                android.util.Log.d("RelayoDebug", "Received from ${incoming.fromAddress}: ${String(incoming.payload)}")
            }
        }
    }
}