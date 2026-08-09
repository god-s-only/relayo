package com.relayo.domain.usecase

import com.relayo.domain.model.VoiceNote
import com.relayo.domain.repository.VoiceNoteRepository
import javax.inject.Inject

class StopRecordingUseCase @Inject constructor(
    private val repository:VoiceNoteRepository
) {
    suspend operator fun invoke():VoiceNote = repository.stopRecordingAndSend()
}