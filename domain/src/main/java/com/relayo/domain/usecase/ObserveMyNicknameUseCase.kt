package com.relayo.domain.usecase

import com.relayo.domain.repository.MeshRepository
import javax.inject.Inject

class ObserveMyNicknameUseCase @Inject constructor(
    private val repository:MeshRepository
) {
    operator fun invoke() = repository.observeMyNickname()
}
