package com.relayo.data.repository

import com.relayo.domain.model.MeshDevice
import com.relayo.domain.repository.MeshRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeMeshRepository @Inject constructor():MeshRepository {

    private val fakeDevices = listOf(
        MeshDevice(
            id = "device-001",
            displayName = "Ada",
            hopCount = 1,
            signalStrength = -42,
            lastSeenEpochMillis = System.currentTimeMillis(),
            isDirectNeighbor = true
        ),
        MeshDevice(
            id = "device-002",
            displayName = "Grace",
            hopCount = 1,
            signalStrength = -58,
            lastSeenEpochMillis = System.currentTimeMillis(),
            isDirectNeighbor = true
        ),
        MeshDevice(
            id = "device-003",
            displayName = "Turing",
            hopCount = 2,
            signalStrength = -71,
            lastSeenEpochMillis = System.currentTimeMillis(),
            isDirectNeighbor = false
        )
    )

    override fun observeNearbyDevices():Flow<List<MeshDevice>> = flow {
        emit(emptyList())
        delay(1200)
        emit(fakeDevices.take(1))
        delay(1000)
        emit(fakeDevices.take(2))
        delay(1000)
        emit(fakeDevices)
    }

    override suspend fun startDiscovery() {
    }

    override suspend fun stopDiscovery() {
    }
}