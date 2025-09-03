package com.bertoferrero.fingerprintcaptureapp.lib

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
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
     * List of MAC addresses to filter (exact matches for hardware filtering)
     */
    private val filterMacs: List<String> = emptyList(),

    /**
     * List of MAC prefixes to filter (software filtering for manufacturer filtering)
     */
    private val filterMacPrefixes: List<String> = emptyList(),

    /**
     * Function to call when a device is found
     */
    private val onDeviceFound: (ScanResult) -> Unit,
) {

    /**
     * BluetoothLeScanner instance
     */
    private var btScanner: BluetoothLeScanner? = null

    /**
     * ScanSettings for optimized BLE scanning
     */
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // High frequency scanning for better data capture
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // Report all advertisement packets
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // More aggressive matching
        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) // Maximum number of matches
        .setReportDelay(0) // Report immediately, no batching
        .build()

    /**
     * Create hardware-level MAC address filters for efficient scanning
     * Note: Hardware filtering with ScanFilter is more efficient but has limitations:
     * - Limited number of filters supported (usually 8-16 depending on hardware)
     * - Exact MAC address matching only for device address filters
     * - Prefix filtering requires software filtering as fallback
     */
    private fun createScanFilters(): List<ScanFilter> {
        return if (filterMacs.isNotEmpty()) {
            // Hardware filtering for exact MAC addresses (most efficient)
            filterMacs.map { macAddress ->
                ScanFilter.Builder()
                    .setDeviceAddress(macAddress)
                    .build()
            }
        } else {
            // No hardware filters - will scan all devices
            // Software filtering will be applied in onResultReceived for prefixes
            emptyList()
        }
    }

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

        /**
         * Common MAC prefixes for known beacon manufacturers
         * Useful for filtering specific types of beacons
         */
        object MacPrefixes {
            const val ESTIMOTE = "D0:39:72"
            const val KONTAKT = "EC:58:8F"
            const val RADIUS_NETWORKS = "D4:CA:6E"
            const val BLUE_SENSE = "C4:AC:59"
            const val MINEW = "AC:23:3F"
            const val FEASYCOM = "84:2E:14"
        }
    }

    /**
     * Get information about the current filtering configuration
     */
    fun getFilteringInfo(): String {
        return when {
            filterMacs.isNotEmpty() -> "Hardware filtering: ${filterMacs.size} exact MAC addresses"
            filterMacPrefixes.isNotEmpty() -> "Software filtering: ${filterMacPrefixes.size} MAC prefixes"
            else -> "No filtering - scanning all devices"
        }
    }

    /**
     * Start scanning for BLE devices
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(context: Context): Boolean {
        // Start scanning for BLE devices
        if(permissionsGranted) {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val btAdapter = btManager.adapter
            btScanner = btAdapter?.bluetoothLeScanner

            if (btAdapter != null && !btAdapter.isEnabled) {
                //Toast.makeText(context, "Please, enable the bluetooth", Toast.LENGTH_SHORT).show()
                Log.e("BleScannerLibrary", "Bluetooth is disabled")
            } else {
                //BLE - Using hardware-level filtering for efficiency
                val scanFilters = createScanFilters()
                btScanner?.startScan(scanFilters, scanSettings, bleScanCallback)
                
                val filterInfo = getFilteringInfo()
                Log.i("BleScannerLibrary", "BLE scan started - $filterInfo")
                
                return true
            }
        }
        return false
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
     * Callback when a device is found - handles both hardware and software filtering
     */
    fun onResultReceived(result: ScanResult){
        val deviceMac = result.device.address
        
        // If we have MAC prefix filters, apply software filtering
        if (filterMacPrefixes.isNotEmpty()) {
            val matchesPrefix = filterMacPrefixes.any { prefix ->
                deviceMac.startsWith(prefix, ignoreCase = true)
            }
            if (!matchesPrefix) {
                Log.d("BleScannerLibrary", "Device $deviceMac doesn't match any prefix filter")
                return
            }
        }
        
        // Log the filtering method used
        when {
            filterMacs.isNotEmpty() -> Log.d("BleScannerLibrary", "Hardware-filtered device found: $deviceMac")
            filterMacPrefixes.isNotEmpty() -> Log.d("BleScannerLibrary", "Prefix-filtered device found: $deviceMac")
            else -> Log.d("BleScannerLibrary", "Device found (no filtering): $deviceMac")
        }
        
        onDeviceFound(result)
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