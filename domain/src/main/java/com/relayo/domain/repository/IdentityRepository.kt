package com.relayo.domain.repository

import com.relayo.domain.model.EphemeralIdentity
import kotlinx.coroutines.flow.Flow

interface IdentityRepository {
    fun observeIdentity():Flow<EphemeralIdentity?>
    suspend fun generateNewIdentity():EphemeralIdentity
    suspend fun wipeIdentity()
}