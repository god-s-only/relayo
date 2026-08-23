package com.relayo.data.repository

import com.relayo.core.crypto.RelayoKeyPairGenerator
import com.relayo.core.crypto.SessionIdGenerator
import com.relayo.domain.model.EphemeralIdentity
import com.relayo.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EphemeralIdentityRepository @Inject constructor(
    private val keyPairGenerator:RelayoKeyPairGenerator,
    private val sessionIdGenerator:SessionIdGenerator
):IdentityRepository {

    private val _identity = MutableStateFlow<EphemeralIdentity?>(null)
    val identityFlow:StateFlow<EphemeralIdentity?> = _identity.asStateFlow()

    override fun observeIdentity() = identityFlow

    override suspend fun generateNewIdentity():EphemeralIdentity {
        val keyPair = keyPairGenerator.generate()
        val identity = EphemeralIdentity(
            sessionId = sessionIdGenerator.generate(),
            publicKeyBytes = keyPair.public.encoded,
            privateKeyBytes = keyPair.private.encoded
        )
        _identity.value = identity
        return identity
    }

    override suspend fun wipeIdentity() {
        _identity.value = null
    }
}
