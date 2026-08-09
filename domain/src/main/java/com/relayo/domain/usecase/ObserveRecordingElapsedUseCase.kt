package com.relayo.domain.usecase

import com.relayo.domain.repository.VoiceNoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecordingElapsedUseCase @Inject constructor(
    private val repository:VoiceNoteRepository
) {
    operator fun invoke():Flow<Long> = repository.observeRecordingElapsedMillis()
}