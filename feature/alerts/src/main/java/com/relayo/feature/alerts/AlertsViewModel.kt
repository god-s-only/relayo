package com.relayo.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.domain.model.AlertSeverity
import com.relayo.domain.model.EmergencyAlert
import com.relayo.domain.usecase.ObserveAlertsUseCase
import com.relayo.domain.usecase.SendAlertUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsUiState(
    val alerts:List<EmergencyAlert> = emptyList(),
    val composerText:String = "",
    val isComposerOpen:Boolean = false,
    val selectedSeverity:AlertSeverity = AlertSeverity.WARNING
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    observeAlerts:ObserveAlertsUseCase,
    private val sendAlert:SendAlertUseCase
):ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState:StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAlerts().collect { alerts ->
                _uiState.value = _uiState.value.copy(alerts = alerts)
            }
        }
    }

    fun onComposerToggled() {
        _uiState.value = _uiState.value.copy(isComposerOpen = !_uiState.value.isComposerOpen)
    }

    fun onComposerTextChanged(text:String) {
        _uiState.value = _uiState.value.copy(composerText = text)
    }

    fun onSeveritySelected(severity:AlertSeverity) {
        _uiState.value = _uiState.value.copy(selectedSeverity = severity)
    }

    fun onSendClicked() {
        val text = _uiState.value.composerText.trim()
        if(text.isEmpty()) return
        val severity = _uiState.value.selectedSeverity
        viewModelScope.launch {
            sendAlert(text, severity)
        }
        _uiState.value = _uiState.value.copy(composerText = "", isComposerOpen = false)
    }
}