package com.relayo.feature.meshstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.domain.model.MeshDevice
import com.relayo.domain.usecase.GenerateIdentityUseCase
import com.relayo.domain.usecase.ObserveNearbyDevicesUseCase
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
    val sessionId:String? = null
)

@HiltViewModel
class MeshStatusViewModel @Inject constructor(
    observeNearbyDevices:ObserveNearbyDevicesUseCase,
    private val generateIdentity:GenerateIdentityUseCase,
    private val wipeIdentity:WipeIdentityUseCase
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
}