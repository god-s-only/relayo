package com.relayo.data.repository

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Base64
import com.relayo.core.mesh.MeshFloodRouter
import com.relayo.core.transport.PeerScanner
import com.relayo.data.wire.NicknameWire
import com.relayo.data.wire.NicknameWireCodec
import com.relayo.domain.filter.ContentFilter
import com.relayo.domain.model.MeshDevice
import com.relayo.domain.repository.IdentityRepository
import com.relayo.domain.repository.MeshRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(InternalSerializationApi::class)
@Singleton
class RealMeshRepository @Inject constructor(
    private val peerScanner:PeerScanner,
    private val floodRouter:MeshFloodRouter,
    private val identityRepository:IdentityRepository,
    private val contentFilter:ContentFilter,
    @ApplicationContext private val context:Context
):MeshRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val devicesById = mutableMapOf<String, MeshDevice>()
    private val _devices = MutableStateFlow<List<MeshDevice>>(emptyList())
    private var scanJob:Job? = null
    private val staleThresholdMillis = 15_000L

    private val peerNicknames = mutableMapOf<String, String>()
    private val peerFingerprints = mutableMapOf<String, String>()
    private val _myNickname = MutableStateFlow<String?>(null)

    @Volatile
    private var currentSessionId:String? = null
    @Volatile
    private var currentPublicKeyBytes:ByteArray? = null

    init {
        repositoryScope.launch {
            identityRepository.observeIdentity().collect { identity ->
                currentSessionId = identity?.sessionId
                currentPublicKeyBytes = identity?.publicKeyBytes
            }
        }
        repositoryScope.launch {
            floodRouter.incomingPayloads.collect { received ->
                if(received.payloadType == "nickname_announce") {
                    handleNicknameAnnounce(received.payloadBytes)
                }
            }
        }
    }

    override fun observeNearbyDevices():Flow<List<MeshDevice>> = _devices.asStateFlow()

    override fun observeMyNickname():Flow<String?> = _myNickname.asStateFlow()

    override suspend fun setMyNickname(nickname:String) {
        val safe = if(contentFilter.isAllowed(nickname)) nickname.trim().take(24) else contentFilter.sanitize(nickname).trim().take(24)
        if(safe.isBlank()) return
        _myNickname.value = safe
        val fingerprint = currentPublicKeyBytes?.let { fingerprintFor(it) } ?: ""
        val wire = NicknameWire(
            senderId = mySenderId(),
            nickname = safe,
            fingerprint = fingerprint,
            timestampEpochMillis = System.currentTimeMillis()
        )
        try {
            floodRouter.broadcast("nickname_announce", NicknameWireCodec.encode(wire))
        } catch(_:Exception) {}
    }

    private fun mySenderId():String {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.address?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" } ?: currentSessionId ?: "unknown"
        } catch(_:SecurityException) {
            currentSessionId ?: "unknown"
        }
    }

    private fun fingerprintFor(publicKeyBytes:ByteArray):String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(publicKeyBytes)
            hash.take(4).joinToString("") { "%02x".format(it) } // 8 hex chars
        } catch(_:Exception) { publicKeyBytes.take(4).joinToString("") { "%02x".format(it) } }
    }

    private fun handleNicknameAnnounce(payloadBytes:ByteArray) {
        val wire = NicknameWireCodec.decode(payloadBytes) ?: return
        if(wire.senderId == mySenderId()) return
        if(!contentFilter.isAllowed(wire.nickname)) return
        peerNicknames[wire.senderId] = wire.nickname
        if(wire.fingerprint.isNotBlank()) {
            peerFingerprints[wire.senderId] = wire.fingerprint
        }
        // Update display names for existing devices
        devicesById[wire.senderId]?.let { existing ->
            devicesById[wire.senderId] = existing.copy(
                displayName = wire.nickname,
                fingerprint = wire.fingerprint
            )
            pruneStaleAndEmit()
        }
    }

    override suspend fun startDiscovery() {
        peerScanner.startAdvertising()
        // Broadcast current nickname if set
        _myNickname.value?.let { nick ->
            val fp = currentPublicKeyBytes?.let { fingerprintFor(it) } ?: ""
            val wire = NicknameWire(
                senderId = mySenderId(),
                nickname = nick,
                fingerprint = fp,
                timestampEpochMillis = System.currentTimeMillis()
            )
            try { floodRouter.broadcast("nickname_announce", NicknameWireCodec.encode(wire)) } catch(_:Exception) {}
        }
        scanJob = repositoryScope.launch {
            peerScanner.scan().collect { peer ->
                val nickname = peerNicknames[peer.address]
                val fingerprint = peerFingerprints[peer.address]
                devicesById[peer.address] = MeshDevice(
                    id = peer.address,
                    displayName = nickname ?: peer.name ?: "Unknown Device",
                    hopCount = 1,
                    signalStrength = peer.rssi,
                    lastSeenEpochMillis = System.currentTimeMillis(),
                    isDirectNeighbor = true,
                    fingerprint = fingerprint
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
