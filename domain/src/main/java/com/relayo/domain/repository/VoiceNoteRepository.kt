package com.relayo.domain.repository

import com.relayo.domain.model.ConversationSummary
import com.relayo.domain.model.VoiceNote
import kotlinx.coroutines.flow.Flow

interface VoiceNoteRepository {
    fun observeVoiceNotes(peerId:String):Flow<List<VoiceNote>>
    fun observeVoiceNoteConversations():Flow<List<ConversationSummary>>
    fun observeRecordingElapsedMillis():Flow<Long>
    suspend fun startRecording(peerId:String)
    suspend fun stopRecordingAndSend():VoiceNote
}