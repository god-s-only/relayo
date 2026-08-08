package com.relayo.domain.usecase

import com.relayo.domain.repository.IdentityRepository
import javax.inject.Inject

class WipeIdentityUseCase @Inject constructor(
    private val repository:IdentityRepository
) {
    suspend operator fun invoke() = repository.wipeIdentity()
}