package com.relayo.domain.usecase

import com.relayo.domain.model.EphemeralIdentity
import com.relayo.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveIdentityUseCase @Inject constructor(
    private val repository:IdentityRepository
) {
    operator fun invoke():Flow<EphemeralIdentity?> = repository.observeIdentity()
}