package com.relayo.data.repository

import com.relayo.core.crypto.AesGcmCipher
import com.relayo.core.crypto.EncryptedPayload
import com.relayo.domain.model.Message
import com.relayo.domain.repository.MessageRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

private data class StoredMessage(
    val id:String,
    val senderId:String,
    val recipientId:String,
    val encrypted:EncryptedPayload,
    val timestampEpochMillis:Long,
    val isFromMe:Boolean
)

@Singleton
class FakeMessageRepository @Inject constructor(
    private val cipher:AesGcmCipher
):MessageRepository {

    // Simulated shared session key — stands in for the real ECDH-derived
    // key that will come from core:mesh peer handshaking later.
    private val sessionKey:SecretKey = cipher.generateKey()

    private val storedMessages = mutableListOf<StoredMessage>()
    private val conversationFlows = mutableMapOf<String, MutableStateFlow<List<Message>>>()

    private fun flowFor(peerId:String):MutableStateFlow<List<Message>> =
        conversationFlows.getOrPut(peerId) { MutableStateFlow(emptyList()) }

    override fun observeConversation(peerId:String) = flowFor(peerId).asStateFlow()

    override suspend fun sendMessage(peerId:String, content:String) {
        val encrypted = cipher.encrypt(sessionKey, content)
        val stored = StoredMessage(
            id = "msg-${System.nanoTime()}",
            senderId = "me",
            recipientId = peerId,
            encrypted = encrypted,
            timestampEpochMillis = System.currentTimeMillis(),
            isFromMe = true
        )
        storedMessages.add(stored)
        emitDecrypted(peerId)

        // Simulate the peer receiving and echoing back — proves the
        // decrypt path independently of the encrypt path.
        delay(600)
        val echoEncrypted = cipher.encrypt(sessionKey, "Got it: $content")
        storedMessages.add(
            StoredMessage(
                id = "msg-${System.nanoTime()}",
                senderId = peerId,
                recipientId = "me",
                encrypted = echoEncrypted,
                timestampEpochMillis = System.currentTimeMillis(),
                isFromMe = false
            )
        )
        emitDecrypted(peerId)
    }

    private fun emitDecrypted(peerId:String) {
        val decrypted = storedMessages
            .filter { it.senderId == peerId || it.recipientId == peerId }
            .map { stored ->
                Message(
                    id = stored.id,
                    senderId = stored.senderId,
                    recipientId = stored.recipientId,
                    content = cipher.decrypt(sessionKey, stored.encrypted),
                    timestampEpochMillis = stored.timestampEpochMillis,
                    isFromMe = stored.isFromMe
                )
            }
        flowFor(peerId).value = decrypted
    }
}