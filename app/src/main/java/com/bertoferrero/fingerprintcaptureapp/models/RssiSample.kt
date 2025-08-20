package com.bertoferrero.fingerprintcaptureapp.models

/**
 * Data class representing a single RSSI sample captured during BLE scanning.
 * Contains the timestamp, MAC address, signal strength and position coordinates.
 */
data class RssiSample(
    val timestamp: Long = System.currentTimeMillis(),
    val macAddress: String,
    val rssi: Int,
    val posX: Float,
    val posY: Float,
    val posZ: Float
)
