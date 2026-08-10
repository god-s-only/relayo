package com.relayo.core.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.relayo.core.transport.DiscoveredPeer
import com.relayo.core.transport.PeerScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlePeerScanner @Inject constructor(
    @ApplicationContext private val context:Context
):PeerScanner {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = bluetoothManager.adapter
    private var advertiseCallback:AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    override fun scan():Flow<DiscoveredPeer> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if(scanner == null) {
            close(IllegalStateException("Bluetooth LE scanner unavailable — is Bluetooth on?"))
            return@callbackFlow
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.RELAYO_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object:ScanCallback() {
            override fun onScanResult(callbackType:Int, result:ScanResult) {
                val device = result.device
                trySend(
                    DiscoveredPeer(
                        address = device.address,
                        name = device.name,
                        rssi = result.rssi
                    )
                )
            }

            override fun onScanFailed(errorCode:Int) {
                close(IllegalStateException("BLE scan failed, error code $errorCode"))
            }
        }

        scanner.startScan(listOf(filter), settings, callback)

        awaitClose {
            scanner.stopScan(callback)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startAdvertising() {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleConstants.RELAYO_SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()

        val callback = object:AdvertiseCallback() {
            override fun onStartFailure(errorCode:Int) {

            }
        }
        advertiseCallback = callback

        advertiser.startAdvertising(settings, data, callback)
    }

    @SuppressLint("MissingPermission")
    override fun stopAdvertising() {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        advertiseCallback?.let { advertiser.stopAdvertising(it) }
        advertiseCallback = null
    }
}