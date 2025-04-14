package com.bertoferrero.fingerprintcaptureapp.lib

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import com.bertoferrero.fingerprintcaptureapp.views.components.resolvePermissions
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.bluetooth.BLUETOOTH_SCAN
import dev.icerock.moko.permissions.location.LOCATION

class BleScanner(
    /**
     * Context to use for scanning
     */
    private val context: Context,

    /**
     * List of MAC addresses to filter
     */
    private val filterMacs: List<String> = emptyList(),

    /**
     * Function to call when a device is found
     */
    private val onDeviceFound: (ScanResult) -> Unit,
) {

    /**
     * BluetoothLeScanner instance
     */
    private var btScanner: BluetoothLeScanner? = null

    companion object {

        /**
         * Permissions granted, used to check if the permissions are granted without calling a composable method
         */
        var permissionsGranted: Boolean = false

        /**
         * Check if the permissions are granted
         */
        @Composable
        fun checkPermissions(): Boolean {
            permissionsGranted = resolvePermissions(
                listOf(
                    Permission.Companion.BLUETOOTH_SCAN,
                    Permission.Companion.LOCATION,
                )
            )
            return permissionsGranted
        }
    }

    /**
     * Start scanning for BLE devices
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        // Start scanning for BLE devices
        if(permissionsGranted) {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val btAdapter = btManager.adapter
            btScanner = btAdapter?.bluetoothLeScanner

            if (btAdapter != null && !btAdapter.isEnabled) {
                Toast.makeText(context, "Please, enable the bluetooth", Toast.LENGTH_SHORT).show()
            } else {
                //BLE
                btScanner?.startScan(bleScanCallback)
            }
        }
    }

    /**
     * Stop scanning for BLE devices
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        // Stop scanning for BLE devices
        btScanner?.stopScan(bleScanCallback)
        btScanner = null
    }

    /**
     * Callback when a device is found, used to filter the devices
     */
    fun onResultReceived(result: ScanResult){
        if (filterMacs.isNotEmpty()){
            val deviceMac = result.device.address
            if(filterMacs.contains(deviceMac)){
                onDeviceFound(result)
            }
            else{
                Log.d("BleScannerLibrary", "Device found: ${result.device.address}, but not in the filter list")
            }
        }
        else{
            onDeviceFound(result)
        }
    }


    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                onResultReceived(it)
                //Log.d("TestRssiMonitorScreen", "Device found: ${it.device.address}, RSSI: ${it.rssi}, TxPower: ${it.txPower}")
                //Toast.makeText(context, "Dispositivo encontrado: ${it.device.address}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach {
                onResultReceived(it)
                //Log.d("TestRssiMonitorScreen", "Batch device found: ${it.device.address}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("TestRssiMonitorScreen", "Scan failed with error: $errorCode")
            //Toast.makeText(context, "Error en el escaneo: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

}