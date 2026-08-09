package com.relayo.domain.usecase

import com.relayo.domain.repository.VoiceNoteRepository
import javax.inject.Inject

class StartRecordingUseCase @Inject constructor(
    private val repository:VoiceNoteRepository
) {
    suspend operator fun invoke(peerId:String) = repository.startRecording(peerId)
}