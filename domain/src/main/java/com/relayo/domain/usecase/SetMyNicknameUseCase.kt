package com.relayo.domain.usecase

import com.relayo.domain.repository.MeshRepository
import javax.inject.Inject

class SetMyNicknameUseCase @Inject constructor(
    private val repository:MeshRepository
) {
    suspend operator fun invoke(nickname:String) = repository.setMyNickname(nickname)
}
