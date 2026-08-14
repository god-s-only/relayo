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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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

    init {
        startServer()
    }

    override fun observeIncoming() = _incoming.asSharedFlow()

    @SuppressLint("MissingPermission")
    private fun startServer() {
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
                    gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }
        }

        gattServer = bluetoothManager.openGattServer(context, callback)
        gattServer?.addService(service)
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendTo(address:String, payload:ByteArray):Boolean {
        val device = adapter?.getRemoteDevice(address) ?: return false
        val gatt = activeConnections[address] ?: connectAndCache(device) ?: return false

        val service = gatt.getService(BleConstants.RELAYO_SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(GattConstants.RELAYO_CHARACTERISTIC_UUID) ?: return false

        return suspendCancellableCoroutine { continuation ->
            characteristic.value = payload
            val started = gatt.writeCharacteristic(characteristic)
            continuation.resume(started)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectAndCache(device:BluetoothDevice):BluetoothGatt? =
        suspendCancellableCoroutine { continuation ->
            val callback = object:BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt:BluetoothGatt, status:Int, newState:Int) {
                    if(newState == BluetoothProfile.STATE_CONNECTED) {
                        activeConnections[device.address] = gatt
                        gatt.discoverServices()
                    } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                        activeConnections.remove(device.address)
                        gatt.close()
                    }
                }

                override fun onServicesDiscovered(gatt:BluetoothGatt, status:Int) {
                    if(continuation.isActive) continuation.resume(gatt)
                }
            }
            device.connectGatt(context, false, callback)
        }
}