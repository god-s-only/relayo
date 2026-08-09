package com.relayo.feature.voicenotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.relayo.domain.model.VoiceNote
import com.relayo.domain.usecase.ObserveRecordingElapsedUseCase
import com.relayo.domain.usecase.ObserveVoiceNotesUseCase
import com.relayo.domain.usecase.StartRecordingUseCase
import com.relayo.domain.usecase.StopRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEMO_PEER_ID = "device-001"

data class VoiceNotesUiState(
    val notes:List<VoiceNote> = emptyList(),
    val isRecording:Boolean = false,
    val elapsedMillis:Long = 0L
)

@HiltViewModel
class VoiceNotesViewModel @Inject constructor(
    observeVoiceNotes:ObserveVoiceNotesUseCase,
    observeElapsed:ObserveRecordingElapsedUseCase,
    private val startRecording:StartRecordingUseCase,
    private val stopRecording:StopRecordingUseCase
):ViewModel() {

    private val _uiState = MutableStateFlow(VoiceNotesUiState())
    val uiState:StateFlow<VoiceNotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeVoiceNotes(DEMO_PEER_ID).collect { notes ->
                _uiState.value = _uiState.value.copy(notes = notes)
            }
        }
        viewModelScope.launch {
            observeElapsed().collect { elapsed ->
                _uiState.value = _uiState.value.copy(elapsedMillis = elapsed)
                if(elapsed == 0L && _uiState.value.isRecording && _uiState.value.notes.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(isRecording = false)
                }
            }
        }
    }

    fun onStartRecording() {
        _uiState.value = _uiState.value.copy(isRecording = true)
        viewModelScope.launch {
            startRecording(DEMO_PEER_ID)
        }
    }

    fun onStopRecording() {
        viewModelScope.launch {
            stopRecording()
            _uiState.value = _uiState.value.copy(isRecording = false)
        }
    }
}