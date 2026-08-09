package com.relayo.domain.usecase

import com.relayo.domain.model.VoiceNote
import com.relayo.domain.repository.VoiceNoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveVoiceNotesUseCase @Inject constructor(
    private val repository:VoiceNoteRepository
) {
    operator fun invoke(peerId:String):Flow<List<VoiceNote>> = repository.observeVoiceNotes(peerId)
}