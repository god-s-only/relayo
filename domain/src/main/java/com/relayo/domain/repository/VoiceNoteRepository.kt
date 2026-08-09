package com.relayo.domain.repository

import com.relayo.domain.model.VoiceNote
import kotlinx.coroutines.flow.Flow

interface VoiceNoteRepository {
    fun observeVoiceNotes(peerId:String):Flow<List<VoiceNote>>
    fun observeRecordingElapsedMillis():Flow<Long>
    suspend fun startRecording(peerId:String)
    suspend fun stopRecordingAndSend():VoiceNote
}