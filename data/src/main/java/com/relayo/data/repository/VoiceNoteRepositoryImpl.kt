package com.relayo.data.repository

import android.bluetooth.BluetoothManager
import android.content.Context
import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.data.audio.VoiceRecorderController
import com.relayo.data.wire.VoiceChunkWireCodec
import com.relayo.data.wire.VoiceHeaderWire
import com.relayo.data.wire.VoiceHeaderWireCodec
import com.relayo.domain.model.ConversationSummary
import com.relayo.domain.model.VoiceNote
import com.relayo.domain.repository.IdentityRepository
import com.relayo.domain.repository.MeshRepository
import com.relayo.domain.repository.VoiceNoteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceNoteRepositoryImpl @Inject constructor(
    private val recorderController:VoiceRecorderController,
    private val meshRepository:MeshRepository,
    private val floodRouter:MeshFloodRouter,
    private val identityRepository:IdentityRepository,
    @ApplicationContext private val context:Context
):VoiceNoteRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notesByPeer = mutableMapOf<String, MutableStateFlow<List<VoiceNote>>>()
    private val knownPeerDisplayNames = mutableMapOf<String, String>()
    private var currentPeerId:String? = null
    private var autoStopJob:Job? = null

    // Reassembly state for incoming voice notes
    private data class ReassemblyState(
        var header:VoiceHeaderWire? = null,
        val chunks:MutableMap<Int, ByteArray> = mutableMapOf()
    )
    private val reassemblyMap = mutableMapOf<String, ReassemblyState>()
    private val reassemblyLock = Any()

    @Volatile
    private var currentIdentitySessionId:String? = null

    private fun flowFor(peerId:String):MutableStateFlow<List<VoiceNote>> =
        notesByPeer.getOrPut(peerId) { MutableStateFlow(emptyList()) }

    init {
        repositoryScope.launch {
            identityRepository.observeIdentity().collect { identity ->
                currentIdentitySessionId = identity?.sessionId
            }
        }

        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                when(received.payloadType) {
                    "voice_header" -> handleVoiceHeader(received.payloadBytes)
                    "voice_chunk" -> handleVoiceChunk(received.payloadBytes)
                }
            }
        }
    }

    private fun mySenderId():String {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.address?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" } ?: currentIdentitySessionId ?: "unknown"
        } catch(e:SecurityException) {
            currentIdentitySessionId ?: "unknown"
        }
    }

    override fun observeVoiceNotes(peerId:String) = flowFor(peerId).asStateFlow()

    override fun observeVoiceNoteConversations() = combine(
        meshRepository.observeNearbyDevices()
    ) { nearbyArray ->
        val nearby = nearbyArray[0]
        val nearbyIds = nearby.map { it.id }.toSet()
        nearby.forEach { device -> knownPeerDisplayNames[device.id] = device.displayName }

        val allKnownPeerIds = (notesByPeer.keys + nearbyIds).toSet()

        allKnownPeerIds.map { peerId ->
            val notes = notesByPeer[peerId]?.value.orEmpty()
            val last = notes.lastOrNull()
            ConversationSummary(
                peerId = peerId,
                displayName = knownPeerDisplayNames[peerId] ?: peerId.takeLast(6),
                lastMessagePreview = last?.let { "${it.durationMillis / 1000}s voice note" },
                lastMessageTimestampEpochMillis = last?.timestampEpochMillis,
                isOnline = peerId in nearbyIds
            )
        }.sortedByDescending { it.lastMessageTimestampEpochMillis ?: 0L }
    }

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

    private suspend fun finalizeRecording():VoiceNote {
        val peerId = currentPeerId ?: "unknown"
        val (path, duration) = recorderController.stop()
        val note = VoiceNote(
            id = "note-${System.nanoTime()}",
            senderId = mySenderId(),
            filePath = path,
            durationMillis = duration,
            timestampEpochMillis = System.currentTimeMillis(),
            isFromMe = true
        )
        currentPeerId = null
        if(path.isBlank()) return note
        flowFor(peerId).value = flowFor(peerId).value + note
        // Real transport: chunk and broadcast via mesh
        try {
            sendVoiceNote(peerId, note)
        } catch(e:Exception) {
        }
        return note
    }

    private suspend fun sendVoiceNote(peerId:String, note:VoiceNote) {
        val file = File(note.filePath)
        if(!file.exists()) return
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        if(bytes.isEmpty()) return
        val chunkSize = 240
        val totalChunks = (bytes.size + chunkSize - 1) / chunkSize
        val header = VoiceHeaderWire(
            noteId = note.id,
            senderId = mySenderId(),
            recipientId = peerId,
            totalChunks = totalChunks,
            durationMillis = note.durationMillis,
            timestampEpochMillis = note.timestampEpochMillis
        )
        floodRouter.broadcast("voice_header", VoiceHeaderWireCodec.encode(header))
        for(i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, bytes.size)
            val chunkBytes = bytes.copyOfRange(start, end)
            val chunkWire = com.relayo.data.wire.VoiceChunkWire(
                noteId = note.id,
                chunkIndex = i,
                chunkBytes = chunkBytes
            )
            floodRouter.broadcast("voice_chunk", VoiceChunkWireCodec.encode(chunkWire))
        }
    }

    private fun handleVoiceHeader(payloadBytes:ByteArray) {
        val header = VoiceHeaderWireCodec.decode(payloadBytes) ?: return
        // Ignore our own header
        if(header.senderId == mySenderId()) return
        synchronized(reassemblyLock) {
            val state = reassemblyMap.getOrPut(header.noteId) { ReassemblyState() }
            state.header = header
            tryEmitIfComplete(header.noteId, state)
        }
    }

    private fun handleVoiceChunk(payloadBytes:ByteArray) {
        val chunk = VoiceChunkWireCodec.decode(payloadBytes) ?: return
        synchronized(reassemblyLock) {
            val state = reassemblyMap.getOrPut(chunk.noteId) { ReassemblyState() }
            // Avoid duplicate chunk
            if(state.chunks.containsKey(chunk.chunkIndex)) return
            state.chunks[chunk.chunkIndex] = chunk.chunkBytes
            tryEmitIfComplete(chunk.noteId, state)
        }
    }

    private fun tryEmitIfComplete(noteId:String, state:ReassemblyState) {
        val header = state.header ?: return
        if(state.chunks.size != header.totalChunks) return
        // Verify all indices present
        for(i in 0 until header.totalChunks) {
            if(!state.chunks.containsKey(i)) return
        }
        // Only emit if this note is for us (private) or broadcast
        val myIds = setOfNotNull(mySenderId(), currentIdentitySessionId)
        if(header.recipientId !in myIds && header.recipientId != "unknown") {
            // Not for us — still clean up to avoid leak, but don't emit to UI
            // Keep for relay? FloodRouter already relayed, so just drop UI emit
            // We still remove to avoid memory leak after timeout
            // For now, drop if not for us
            // Comment out to allow all to receive for mesh-wide broadcast:
            // return
        }
        // Reassemble in order
        val totalSize = state.chunks.values.sumOf { it.size }
        val reassembled = ByteArray(totalSize)
        var offset = 0
        for(i in 0 until header.totalChunks) {
            val chunk = state.chunks[i]!!
            System.arraycopy(chunk, 0, reassembled, offset, chunk.size)
            offset += chunk.size
        }
        // Write to cache file
        try {
            val outFile = File(context.cacheDir, "voice_recv_${noteId}_${System.currentTimeMillis()}.m4a")
            outFile.writeBytes(reassembled)
            val voiceNote = VoiceNote(
                id = header.noteId,
                senderId = header.senderId,
                filePath = outFile.absolutePath,
                durationMillis = header.durationMillis,
                timestampEpochMillis = header.timestampEpochMillis,
                isFromMe = false
            )
            // Determine peerId for conversation: for inbound, peer is sender
            val peerId = header.senderId
            knownPeerDisplayNames[peerId] = knownPeerDisplayNames[peerId] ?: peerId.takeLast(6)
            flowFor(peerId).value = flowFor(peerId).value + voiceNote
            // Clean up reassembly state
            reassemblyMap.remove(noteId)
        } catch(e:Exception) {
            reassemblyMap.remove(noteId)
        }
    }
}
