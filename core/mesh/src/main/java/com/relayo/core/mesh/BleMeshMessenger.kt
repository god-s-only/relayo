package com.relayo.core.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.relayo.core.transport.IncomingBytes
import com.relayo.core.transport.MeshMessenger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BleMeshMessenger @Inject constructor(
    @ApplicationContext private val context:Context
):MeshMessenger {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = bluetoothManager.adapter

    private val _incoming = MutableSharedFlow<IncomingBytes>(extraBufferCapacity = 32)
    private var gattServer:BluetoothGattServer? = null
    private val activeConnections = mutableMapOf<String, BluetoothGatt>()
    private val lastUsedMap = mutableMapOf<String, Long>()
    private val connectionLock = Any()
    private val pendingWrites = mutableMapOf<String, kotlinx.coroutines.CancellableContinuation<Boolean>>()
    private val pendingWritesLock = Any()
    private var isStarted = false
    private var cleanupJob:Job? = null

    companion object {
        private const val IDLE_TIMEOUT_MS = 60_000L
        private const val CLEANUP_INTERVAL_MS = 30_000L
    }

    override fun observeIncoming() = _incoming.asSharedFlow()

    @SuppressLint("MissingPermission")
    override fun start() {
        if(isStarted) return
        try {
            val characteristic = BluetoothGattCharacteristic(
                GattConstants.RELAYO_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
            )
            val service = BluetoothGattService(
                BleConstants.RELAYO_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            service.addCharacteristic(characteristic)

            val callback = object:BluetoothGattServerCallback() {
                override fun onCharacteristicWriteRequest(
                    device:BluetoothDevice,
                    requestId:Int,
                    characteristic:BluetoothGattCharacteristic,
                    preparedWrite:Boolean,
                    responseNeeded:Boolean,
                    offset:Int,
                    value:ByteArray
                ) {
                    updateLastUsed(device.address)
                    _incoming.tryEmit(IncomingBytes(fromAddress = device.address, payload = value))
                    if(responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    }
                }
            }

            gattServer = bluetoothManager.openGattServer(context, callback)
            gattServer?.addService(service)
            isStarted = true
            startCleanupJob()
        } catch(e:SecurityException) {
        }
    }

    private fun startCleanupJob() {
        cleanupJob?.cancel()
        cleanupJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while(true) {
                delay(CLEANUP_INTERVAL_MS)
                cleanupIdleConnections()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        val toClose = mutableListOf<Pair<String, BluetoothGatt>>()
        synchronized(connectionLock) {
            val iterator = lastUsedMap.entries.iterator()
            while(iterator.hasNext()) {
                val entry = iterator.next()
                if(now - entry.value > IDLE_TIMEOUT_MS) {
                    val gatt = activeConnections[entry.key]
                    if(gatt != null) {
                        toClose.add(entry.key to gatt)
                    }
                    iterator.remove()
                    activeConnections.remove(entry.key)
                    synchronized(pendingWritesLock) {
                        pendingWrites.remove(entry.key)?.let { cont ->
                            if(cont.isActive) cont.resume(false)
                        }
                    }
                }
            }
        }
        toClose.forEach { (address, gatt) ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch(_:SecurityException) {} catch(_:Exception) {}
        }
    }

    private fun updateLastUsed(address:String) {
        synchronized(connectionLock) {
            lastUsedMap[address] = System.currentTimeMillis()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendTo(address:String, payload:ByteArray):Boolean {
        if(!isStarted) return false
        return try {
            val device = adapter?.getRemoteDevice(address) ?: return false
            val gatt = synchronized(connectionLock) { activeConnections[address] } ?: try {
                withTimeout(5_000) { connectAndCache(device) }
            } catch(e:TimeoutCancellationException) {
                null
            } ?: return false

            val service = gatt.getService(BleConstants.RELAYO_SERVICE_UUID) ?: return false
            val characteristic = service.getCharacteristic(GattConstants.RELAYO_CHARACTERISTIC_UUID) ?: return false

            try {
                withTimeout(6_000) {
                    suspendCancellableCoroutine { continuation ->
                        synchronized(pendingWritesLock) {
                            if(pendingWrites.containsKey(address)) {
                                if(continuation.isActive) continuation.resume(false)
                                return@suspendCancellableCoroutine
                            }
                            pendingWrites[address] = continuation
                        }

                        continuation.invokeOnCancellation {
                            synchronized(pendingWritesLock) {
                                pendingWrites.remove(address)
                            }
                        }

                        characteristic.value = payload
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        val started = gatt.writeCharacteristic(characteristic)
                        if(!started) {
                            synchronized(pendingWritesLock) {
                                pendingWrites.remove(address)
                            }
                            if(continuation.isActive) continuation.resume(false)
                        }
                    }
                }.also { success ->
                    if(success) updateLastUsed(address)
                    success
                }
            } catch(e:TimeoutCancellationException) {
                synchronized(pendingWritesLock) {
                    pendingWrites.remove(address)
                }
                false
            }
        } catch(e:SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectAndCache(device:BluetoothDevice):BluetoothGatt? =
        suspendCancellableCoroutine { continuation ->
            try {
                val callback = object:BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt:BluetoothGatt, status:Int, newState:Int) {
                        if(newState == BluetoothProfile.STATE_CONNECTED) {
                            synchronized(connectionLock) {
                                activeConnections[device.address] = gatt
                                lastUsedMap[device.address] = System.currentTimeMillis()
                            }
                            gatt.discoverServices()
                        } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                            synchronized(connectionLock) {
                                activeConnections.remove(device.address)
                                lastUsedMap.remove(device.address)
                            }
                            synchronized(pendingWritesLock) {
                                pendingWrites.remove(device.address)?.let { cont ->
                                    if(cont.isActive) cont.resume(false)
                                }
                            }
                            gatt.close()
                            if(continuation.isActive && status != BluetoothGatt.GATT_SUCCESS) {
                                continuation.resume(null)
                            }
                        }
                    }

                    override fun onServicesDiscovered(gatt:BluetoothGatt, status:Int) {
                        if(status == BluetoothGatt.GATT_SUCCESS) {
                            updateLastUsed(device.address)
                            if(continuation.isActive) continuation.resume(gatt)
                        } else {
                            if(continuation.isActive) continuation.resume(null)
                        }
                    }

                    override fun onCharacteristicWrite(
                        gatt:BluetoothGatt,
                        characteristic:BluetoothGattCharacteristic,
                        status:Int
                    ) {
                        val addr = gatt.device.address
                        if(status == BluetoothGatt.GATT_SUCCESS) updateLastUsed(addr)
                        val cont = synchronized(pendingWritesLock) {
                            pendingWrites.remove(addr)
                        }
                        cont?.let {
                            if(it.isActive) it.resume(status == BluetoothGatt.GATT_SUCCESS)
                        }
                    }
                }
                device.connectGatt(context, false, callback)
            } catch(e:SecurityException) {
                if(continuation.isActive) continuation.resume(null)
            }
        }
}
