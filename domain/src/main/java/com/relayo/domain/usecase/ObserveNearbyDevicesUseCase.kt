package com.relayo.domain.usecase

import com.relayo.domain.model.MeshDevice
import com.relayo.domain.repository.MeshRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNearbyDevicesUseCase @Inject constructor(
    private val repository:MeshRepository
) {
    operator fun invoke():Flow<List<MeshDevice>> = repository.observeNearbyDevices()
}