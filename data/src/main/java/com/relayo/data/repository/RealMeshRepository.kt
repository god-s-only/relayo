package com.relayo.data.repository

import com.relayo.core.transport.PeerScanner
import com.relayo.domain.model.MeshDevice
import com.relayo.domain.repository.MeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealMeshRepository @Inject constructor(
    private val peerScanner:PeerScanner
):MeshRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val devicesById = mutableMapOf<String, MeshDevice>()
    private val _devices = MutableStateFlow<List<MeshDevice>>(emptyList())
    private var scanJob:Job? = null
    private val staleThresholdMillis = 15_000L

    override fun observeNearbyDevices():Flow<List<MeshDevice>> = _devices.asStateFlow()

    override suspend fun startDiscovery() {
        peerScanner.startAdvertising()
        scanJob = repositoryScope.launch {
            peerScanner.scan().collect { peer ->
                devicesById[peer.address] = MeshDevice(
                    id = peer.address,
                    displayName = peer.name ?: "Unknown Device",
                    hopCount = 1,
                    signalStrength = peer.rssi,
                    lastSeenEpochMillis = System.currentTimeMillis(),
                    isDirectNeighbor = true
                )
                pruneStaleAndEmit()
            }
        }
    }

    override suspend fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
        peerScanner.stopAdvertising()
    }

    private fun pruneStaleAndEmit() {
        val now = System.currentTimeMillis()
        devicesById.entries.removeAll { (_, device) ->
            now - device.lastSeenEpochMillis > staleThresholdMillis
        }
        _devices.value = devicesById.values.sortedByDescending { it.signalStrength }
    }
}