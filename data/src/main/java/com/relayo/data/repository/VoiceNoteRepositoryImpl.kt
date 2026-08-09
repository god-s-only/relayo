package com.relayo.data.repository

import com.relayo.data.audio.VoiceRecorderController
import com.relayo.domain.model.VoiceNote
import com.relayo.domain.repository.VoiceNoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceNoteRepositoryImpl @Inject constructor(
    private val recorderController:VoiceRecorderController
):VoiceNoteRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notesByPeer = mutableMapOf<String, MutableStateFlow<List<VoiceNote>>>()
    private var currentPeerId:String? = null
    private var autoStopJob:Job? = null

    private fun flowFor(peerId:String):MutableStateFlow<List<VoiceNote>> =
        notesByPeer.getOrPut(peerId) { MutableStateFlow(emptyList()) }

    override fun observeVoiceNotes(peerId:String) = flowFor(peerId).asStateFlow()

    override fun observeRecordingElapsedMillis() = recorderController.elapsedMillis

    override suspend fun startRecording(peerId:String) {
        currentPeerId = peerId
        recorderController.start(repositoryScope)
        autoStopJob = repositoryScope.launch {
            recorderController.elapsedMillis.collect { elapsed ->
                if(elapsed >= VoiceRecorderController.MAX_DURATION_MILLIS) {
                    finalizeRecording()
                    this.coroutineContext.job.cancel()
                }
            }
        }
    }

    override suspend fun stopRecordingAndSend():VoiceNote {
        autoStopJob?.cancel()
        return finalizeRecording()
    }

    private fun finalizeRecording():VoiceNote {
        val peerId = currentPeerId ?: "unknown"
        val (path, duration) = recorderController.stop()
        val note = VoiceNote(
            id = "note-${System.nanoTime()}",
            senderId = "me",
            filePath = path,
            durationMillis = duration,
            timestampEpochMillis = System.currentTimeMillis(),
            isFromMe = true
        )
        currentPeerId = null
        if(path.isBlank()) {
            return note
        }
        flowFor(peerId).value = flowFor(peerId).value + note
        return note
    }
}