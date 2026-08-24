package com.relayo.domain.repository

import com.relayo.domain.model.MeshDevice
import kotlinx.coroutines.flow.Flow

interface MeshRepository {
    fun observeNearbyDevices():Flow<List<MeshDevice>>
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    fun observeMyNickname():Flow<String?> = kotlinx.coroutines.flow.flowOf(null)
    suspend fun setMyNickname(nickname:String) {}
}