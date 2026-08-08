package com.relayo.domain.usecase

import com.relayo.domain.model.EphemeralIdentity
import com.relayo.domain.repository.IdentityRepository
import javax.inject.Inject

class GenerateIdentityUseCase @Inject constructor(
    private val repository:IdentityRepository
) {
    suspend operator fun invoke():EphemeralIdentity = repository.generateNewIdentity()
}