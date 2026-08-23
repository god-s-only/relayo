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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val pendingWrites = mutableMapOf<String, kotlinx.coroutines.CancellableContinuation<Boolean>>()
    private val pendingWritesLock = Any()
    private var isStarted = false

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
                    _incoming.tryEmit(IncomingBytes(fromAddress = device.address, payload = value))
                    if(responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    }
                }
            }

            gattServer = bluetoothManager.openGattServer(context, callback)
            gattServer?.addService(service)
            isStarted = true
        } catch(e:SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendTo(address:String, payload:ByteArray):Boolean {
        if(!isStarted) return false
        return try {
            val device = adapter?.getRemoteDevice(address) ?: return false
            val gatt = activeConnections[address] ?: try {
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
                            activeConnections[device.address] = gatt
                            gatt.discoverServices()
                        } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                            activeConnections.remove(device.address)
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
