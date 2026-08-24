package com.relayo.core.mesh

import com.relayo.core.transport.MeshMessenger
import com.relayo.domain.repository.MeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshSessionManager @Inject constructor(
    private val messenger:MeshMessenger,
    private val floodRouter:MeshFloodRouter,
    private val meshRepository:MeshRepository,
    private val gossipManager:GossipManager
) {

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun startIfNeeded() {
        if(started) return
        started = true
        messenger.start()
        floodRouter.start()
        gossipManager.start()
        sessionScope.launch {
            meshRepository.startDiscovery()
        }
    }

    fun stopIfNeeded() {
        if(!started) return
        started = false
        sessionScope.launch {
            try { meshRepository.stopDiscovery() } catch(_:Exception) {}
        }
    }
}